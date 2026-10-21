import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;

import com.clussmanproductions.trafficcontrol.ModBlocks;
import com.clussmanproductions.trafficcontrol.ModTrafficControl;
import com.clussmanproductions.trafficcontrol.blocks.BlockCrossingGateGate;
import com.clussmanproductions.trafficcontrol.blocks.BlockCrossingRelayNE;
import com.clussmanproductions.trafficcontrol.blocks.BlockCrossingRelayNW;
import com.clussmanproductions.trafficcontrol.blocks.BlockCrossingRelaySE;
import com.clussmanproductions.trafficcontrol.blocks.BlockCrossingRelaySW;
import com.clussmanproductions.trafficcontrol.blocks.BlockCrossingRelayTopNE;
import com.clussmanproductions.trafficcontrol.blocks.BlockCrossingRelayTopNW;
import com.clussmanproductions.trafficcontrol.blocks.BlockCrossingRelayTopSE;
import com.clussmanproductions.trafficcontrol.blocks.BlockCrossingRelayTopSW;
import com.clussmanproductions.trafficcontrol.item.ItemCrossingRelayBox;
import com.clussmanproductions.trafficcontrol.tileentity.CrossingGateGateTileEntity;
import com.clussmanproductions.trafficcontrol.tileentity.RelayTileEntity;

import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockTorch;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeProviderSingle;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.SaveHandlerMP;
import net.minecraft.world.storage.WorldInfo;

/**
 * Drives a crossing relay box and a paired crossing gate in a real (in memory) server World:
 * the signal comes and goes the way MazeCity plugins and players give it (lever, redstone block,
 * torch set and removed, block removed without a block update) and the gate must follow.
 */
public class RelayTest {

	static class TestProvider extends WorldProviderSurface {
		@Override
		protected void init() {
			hasSkyLight = true;
			biomeProvider = new BiomeProviderSingle(Biomes.PLAINS);
		}
	}

	/** Server side World whose chunks are all loaded and empty; no lighting */
	static class TestWorld extends World {
		TestWorld() {
			super(new SaveHandlerMP(), new WorldInfo(new WorldSettings(0L, GameType.CREATIVE, false, false, WorldType.FLAT), "relaytest"),
					new TestProvider(), new Profiler(), false);
			provider.setWorld(this);
			chunkProvider = createChunkProvider();
		}

		@Override
		protected IChunkProvider createChunkProvider() {
			final World world = this;
			return new IChunkProvider() {
				final Map<Long, Chunk> chunks = new HashMap<Long, Chunk>();

				public Chunk getLoadedChunk(int x, int z) { return provideChunk(x, z); }

				public Chunk provideChunk(int x, int z) {
					Long key = ChunkPos.asLong(x, z);
					Chunk chunk = chunks.get(key);
					if (chunk == null) {
						chunk = new Chunk(world, x, z);
						chunks.put(key, chunk);
					}
					return chunk;
				}

				public boolean tick() { return false; }
				public String makeString() { return "relaytest"; }
				public boolean isChunkGeneratedAt(int x, int z) { return true; }
			};
		}

		@Override
		protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) { return true; }

		@Override
		public boolean checkLightFor(EnumSkyBlock lightType, BlockPos pos) { return true; }
	}

	// Relay box placed from (0, 0, 0) facing north: bottom layer at y = 1, master (SE) at (0, 1, 0), SW at (-1, 1, 0)
	static final BlockPos CLICKED = new BlockPos(0, 0, 0);
	static final BlockPos MASTER = new BlockPos(0, 1, 0);
	static final BlockPos EAST_OF_MASTER = new BlockPos(1, 1, 0);
	static final BlockPos WEST_OF_SW = new BlockPos(-2, 1, 0);
	static final BlockPos GATE = new BlockPos(6, 1, 6);
	static final BlockPos NORTH_OF_NW = new BlockPos(-1, 1, -2);
	static final BlockPos UNDER_SW = new BlockPos(-1, 0, 0);
	static final BlockPos INVERTER_INPUT = new BlockPos(2, 1, 0);

	static TestWorld world;
	static RelayTileEntity relay;
	static CrossingGateGateTileEntity gate;
	static int failures = 0;
	static long time = 0;

	public static void main(String[] args) throws Exception {
		net.minecraft.init.Bootstrap.register();
		ModTrafficControl.logger = LogManager.getLogger("RelayTest");
		ModBlocks.crossing_relay_se = new BlockCrossingRelaySE();
		ModBlocks.crossing_relay_sw = new BlockCrossingRelaySW();
		ModBlocks.crossing_relay_nw = new BlockCrossingRelayNW();
		ModBlocks.crossing_relay_ne = new BlockCrossingRelayNE();
		ModBlocks.crossing_relay_top_se = new BlockCrossingRelayTopSE();
		ModBlocks.crossing_relay_top_sw = new BlockCrossingRelayTopSW();
		ModBlocks.crossing_relay_top_nw = new BlockCrossingRelayTopNW();
		ModBlocks.crossing_relay_top_ne = new BlockCrossingRelayTopNE();
		ModBlocks.crossing_gate_gate = new BlockCrossingGateGate();

		System.out.println("1. levier (contact du bloc SW)");
		setUp();
		toggleLever(WEST_OF_SW);
		expect("levier leve", true, CrossingGateGateTileEntity.EnumStatuses.Closed);
		toggleLever(WEST_OF_SW);
		expect("levier baisse", false, CrossingGateGateTileEntity.EnumStatuses.Open);

		System.out.println("2. bloc de redstone pose puis casse (douane MazeJobs)");
		setUp();
		world.setBlockState(EAST_OF_MASTER, Blocks.REDSTONE_BLOCK.getDefaultState());
		expect("bloc pose", true, CrossingGateGateTileEntity.EnumStatuses.Closed);
		world.setBlockToAir(EAST_OF_MASTER);
		expect("bloc casse", false, CrossingGateGateTileEntity.EnumStatuses.Open);

		System.out.println("3. torche de redstone posee puis retiree (terminal, actionneur torch)");
		setUp();
		world.setBlockState(EAST_OF_MASTER, Blocks.REDSTONE_TORCH.getDefaultState().withProperty(BlockTorch.FACING, EnumFacing.UP));
		expect("torche posee", true, CrossingGateGateTileEntity.EnumStatuses.Closed);
		world.setBlockToAir(EAST_OF_MASTER);
		expect("torche retiree", false, CrossingGateGateTileEntity.EnumStatuses.Open);

		System.out.println("4. deux sources sur deux blocs du boitier : couper l'une ne doit pas ouvrir");
		setUp();
		world.setBlockState(EAST_OF_MASTER, Blocks.REDSTONE_BLOCK.getDefaultState());
		toggleLever(WEST_OF_SW);
		expect("bloc + levier", true, CrossingGateGateTileEntity.EnumStatuses.Closed);
		toggleLever(WEST_OF_SW);
		expect("levier baisse, bloc toujours la", true, CrossingGateGateTileEntity.EnumStatuses.Closed);
		world.setBlockToAir(EAST_OF_MASTER);
		expect("bloc casse aussi", false, CrossingGateGateTileEntity.EnumStatuses.Open);

		System.out.println("5. source retiree sans mise a jour de bloc (plugin sans physique, WorldEdit)");
		setUp();
		world.setBlockState(EAST_OF_MASTER, Blocks.REDSTONE_BLOCK.getDefaultState());
		expect("bloc pose", true, CrossingGateGateTileEntity.EnumStatuses.Closed);
		world.setBlockState(EAST_OF_MASTER, Blocks.AIR.getDefaultState(), 2);
		expect("bloc efface sans voisins", false, CrossingGateGateTileEntity.EnumStatuses.Open);

		System.out.println("6. torche d'inverseur toujours allumee : un bloc pose contre un autre bloc du boitier");
		setUp();
		placeInverterTorch();
		expect("torche allumee", true, CrossingGateGateTileEntity.EnumStatuses.Closed);
		world.setBlockState(NORTH_OF_NW, Blocks.STONE.getDefaultState());
		expect("bloc pose au nord du bloc NW", true, CrossingGateGateTileEntity.EnumStatuses.Closed);

		System.out.println("7. torche d'inverseur toujours allumee : l'herbe pousse sous le boitier");
		setUp();
		placeInverterTorch();
		expect("torche allumee", true, CrossingGateGateTileEntity.EnumStatuses.Closed);
		world.setBlockState(UNDER_SW, Blocks.GRASS.getDefaultState());
		expect("terre devenue herbe sous SW", true, CrossingGateGateTileEntity.EnumStatuses.Closed);

		System.out.println("8. boitier casse bloc par bloc sous tension : aucune exception");
		setUp();
		world.setBlockState(EAST_OF_MASTER, Blocks.REDSTONE_BLOCK.getDefaultState());
		tick(5);
		try {
			for (BlockPos part : BlockPos.getAllInBox(new BlockPos(-1, 1, -1), new BlockPos(0, 2, 0))) {
				world.destroyBlock(part, false);
			}
			System.out.println("  ok   boitier casse");
		}
		catch (Throwable t) {
			failures++;
			System.out.println("  FAIL boitier casse : " + t);
			t.printStackTrace(System.out);
		}

		System.out.println(failures == 0 ? "TOUT OK" : failures + " ECHEC(S)");
		System.exit(failures == 0 ? 0 : 1);
	}

	/** A fresh world: stone floor, relay box, gate paired to it and open */
	static void setUp() throws Exception {
		world = new TestWorld();
		time = 0;
		for (BlockPos floor : BlockPos.getAllInBox(new BlockPos(-6, 0, -6), new BlockPos(10, 0, 10))) {
			world.setBlockState(floor, Blocks.STONE.getDefaultState());
		}

		Method place = ItemCrossingRelayBox.class.getDeclaredMethod("placeMultiblock", World.class, BlockPos.class, EnumFacing.class);
		place.setAccessible(true);
		place.invoke(new ItemCrossingRelayBox(), world, CLICKED, EnumFacing.NORTH);
		relay = (RelayTileEntity)world.getTileEntity(MASTER);

		world.setBlockState(GATE, ModBlocks.crossing_gate_gate.getDefaultState());
		gate = (CrossingGateGateTileEntity)world.getTileEntity(GATE);
		relay.addOrRemoveCrossingGateGate(gate);

		world.setBlockState(WEST_OF_SW, Blocks.LEVER.getDefaultState().withProperty(BlockLever.FACING, BlockLever.EnumOrientation.UP_X));
		tick(20);
	}

	/** Output of a redstone inverter: lit torch on the side of an unpowered block, against the master */
	static void placeInverterTorch() {
		world.setBlockState(INVERTER_INPUT, Blocks.STONE.getDefaultState());
		world.setBlockState(EAST_OF_MASTER, Blocks.REDSTONE_TORCH.getDefaultState().withProperty(BlockTorch.FACING, EnumFacing.WEST));
	}

	/** What BlockLever.onBlockActivated does on the server */
	static void toggleLever(BlockPos pos) {
		IBlockState state = world.getBlockState(pos).cycleProperty(BlockLever.POWERED);
		world.setBlockState(pos, state, 3);
		world.notifyNeighborsOfStateChange(pos, Blocks.LEVER, false);
		world.notifyNeighborsOfStateChange(pos.down(), Blocks.LEVER, false);
	}

	static void tick(int ticks) {
		for (int i = 0; i < ticks; i++) {
			world.getWorldInfo().setWorldTotalTime(++time);
			RelayTileEntity master = (RelayTileEntity)world.getTileEntity(MASTER);
			if (master != null) {
				master.update();
			}
			gate.update();
		}
	}

	/** Long enough for the closing delay (4 s) and a full travel of the arm (60 degrees at 10 per second) */
	static void expect(String step, boolean powered, CrossingGateGateTileEntity.EnumStatuses status) {
		tick(20 * 12);
		boolean ok = relay.getPowered() == powered && gate.getStatus() == status;
		if (!ok) {
			failures++;
		}
		System.out.printf("  %-4s %-34s boitier %-9s barriere %-7s (%.1f deg) %s%n",
				ok ? "ok" : "FAIL", step, relay.getPowered() ? "alimente" : "coupe", gate.getStatus(), gate.getGateRotation(),
				ok ? "" : "(attendu " + (powered ? "alimente" : "coupe") + " / " + status + ")");
	}
}

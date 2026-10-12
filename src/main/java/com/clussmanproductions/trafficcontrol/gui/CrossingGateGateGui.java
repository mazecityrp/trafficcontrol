package com.clussmanproductions.trafficcontrol.gui;

import java.io.IOException;

import org.lwjgl.input.Keyboard;

import com.clussmanproductions.trafficcontrol.tileentity.CrossingGateGateTileEntity;
import com.clussmanproductions.trafficcontrol.tileentity.CrossingGateGateTileEntity.GateLightCount;
import com.clussmanproductions.trafficcontrol.util.ConfigPermissions;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraftforge.fml.client.config.GuiCheckBox;

public class CrossingGateGateGui extends GuiScreen {
	private static final int ROW_HEIGHT = 24;
	private static final int VALID_COLOR = 0xE0E0E0;
	private static final int INVALID_COLOR = 0xFF5555;
	private static final String HEIGHT_LABEL = "Gate Height (" + (int)CrossingGateGateTileEntity.MIN_GATE_HEIGHT + "-" + (int)CrossingGateGateTileEntity.MAX_GATE_HEIGHT + " px):";
	private static final String THICKNESS_LABEL = "Gate Thickness (" + (int)CrossingGateGateTileEntity.MIN_GATE_THICKNESS + "-" + (int)CrossingGateGateTileEntity.MAX_GATE_THICKNESS + " px):";
	private static final String SPEED_LABEL = "Gate Speed (" + (int)CrossingGateGateTileEntity.MIN_GATE_SPEED + "-" + (int)CrossingGateGateTileEntity.MAX_GATE_SPEED + " \u00b0/s):";
	
	private GuiTextField length;
	private GuiTextField gateHeight;
	private GuiTextField gateThickness;
	private GuiTextField upperRotation;
	private GuiTextField lowerRotation;
	private GuiTextField speed;
	private GuiTextField delay;
	private GuiTextField lightStartOffset;
	private GuiCheckBox threeLights;
	private GuiCheckBox oneLight;

	private CrossingGateGateTileEntity te;
	public CrossingGateGateGui(CrossingGateGateTileEntity te)
	{
		this.te = te;
	}
	
	@Override
	public void initGui() {
		super.initGui();
		
		int horizontalCenter = width / 2;
		int verticalCenter = height / 2;
		
		// 8 rows of fields then the light checkboxes, centred vertically
		int x = horizontalCenter - 50;
		int y = verticalCenter - (8 * ROW_HEIGHT + 20) / 2;
		
		// Dimensions of the arm: X (length), Y (height), Z (thickness)
		length = new GuiTextField(COMP_IDS.LENGTH, fontRenderer, x, y, 100, 20);
		length.setText(String.valueOf(te.getCrossingGateLength()));
		
		gateHeight = new GuiTextField(COMP_IDS.GATE_HEIGHT, fontRenderer, x, y += ROW_HEIGHT, 100, 20);
		gateHeight.setMaxStringLength(6);
		gateHeight.setText(String.valueOf(te.getGateHeight()));
		
		gateThickness = new GuiTextField(COMP_IDS.GATE_THICKNESS, fontRenderer, x, y += ROW_HEIGHT, 100, 20);
		gateThickness.setMaxStringLength(6);
		gateThickness.setText(String.valueOf(te.getGateThickness()));
		
		upperRotation = new GuiTextField(COMP_IDS.UPPER_ROTATION, fontRenderer, x, y += ROW_HEIGHT, 100, 20);
		upperRotation.setText(String.valueOf(te.getUpperRotationLimit()));
		
		lowerRotation = new GuiTextField(COMP_IDS.LOWER_ROTATION, fontRenderer, x, y += ROW_HEIGHT, 100, 20);
		lowerRotation.setText(String.valueOf(te.getLowerRotationLimit()));
		
		speed = new GuiTextField(COMP_IDS.SPEED, fontRenderer, x, y += ROW_HEIGHT, 100, 20);
		speed.setMaxStringLength(6);
		speed.setText(String.valueOf(te.getGateSpeed()));
		
		delay = new GuiTextField(COMP_IDS.DELAY, fontRenderer, x, y += ROW_HEIGHT, 100, 20);
		delay.setText(String.valueOf(te.getDelay()));
		
		lightStartOffset = new GuiTextField(COMP_IDS.LIGHT_START_OFFSET, fontRenderer, x, y += ROW_HEIGHT, 100, 20);
		lightStartOffset.setText(String.valueOf(te.getLightStartOffset()));
		
		y += ROW_HEIGHT;
		threeLights = new GuiCheckBox(COMP_IDS.THREE_LIGHT, x, y, "Three Gate Lights", te.getGateLightCount() == GateLightCount.ThreeLights);
		oneLight = new GuiCheckBox(COMP_IDS.ONE_LIGHT, x + threeLights.width + 4, y, "One Gate Light", te.getGateLightCount() == GateLightCount.OneLight);
		
		updateRangeColors();
		
		buttonList.add(threeLights);
		buttonList.add(oneLight);
	}
	
	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		super.drawScreen(mouseX, mouseY, partialTicks);
		int horizontalCenter = width / 2;
		int verticalCenter = height / 2;
		
		fontRenderer.drawString("Gate Length:", length.x - fontRenderer.getStringWidth("Gate Length:") - 10, length.y + (length.height / 4), 0xFFFFFF);
		length.drawTextBox();
		
		fontRenderer.drawString(HEIGHT_LABEL, gateHeight.x - fontRenderer.getStringWidth(HEIGHT_LABEL) - 10, gateHeight.y + (gateHeight.height / 4), 0xFFFFFF);
		gateHeight.drawTextBox();
		
		fontRenderer.drawString(THICKNESS_LABEL, gateThickness.x - fontRenderer.getStringWidth(THICKNESS_LABEL) - 10, gateThickness.y + (gateThickness.height / 4), 0xFFFFFF);
		gateThickness.drawTextBox();
		
		fontRenderer.drawString("Upper Rotation:", upperRotation.x - fontRenderer.getStringWidth("Upper Rotation:") - 10, upperRotation.y + (upperRotation.height / 4), 0xFFFFFF);
		upperRotation.drawTextBox();
		
		fontRenderer.drawString("Lower Rotation:", lowerRotation.x - fontRenderer.getStringWidth("Lower Rotation:") - 10, lowerRotation.y + (lowerRotation.height / 4), 0xFFFFFF);
		lowerRotation.drawTextBox();
		
		fontRenderer.drawString(SPEED_LABEL, speed.x - fontRenderer.getStringWidth(SPEED_LABEL) - 10, speed.y + (speed.height / 4), 0xFFFFFF);
		speed.drawTextBox();
		
		fontRenderer.drawString("Activation Delay:", delay.x - fontRenderer.getStringWidth("Activation Delay:") - 10, delay.y + (delay.height / 4), 0xFFFFFF);
		delay.drawTextBox();
		
		fontRenderer.drawString("Light Start Offset:", lightStartOffset.x - fontRenderer.getStringWidth("Light Start Offset:") - 10, lightStartOffset.y + (lightStartOffset.height / 4), 0xFFFFFF);
		lightStartOffset.drawTextBox();
	}
	
	@Override
	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		super.keyTyped(typedChar, keyCode);
		
		if (Character.isDigit(typedChar) ||
				keyCode == Keyboard.KEY_DELETE || 
				keyCode == Keyboard.KEY_BACK ||
				keyCode == Keyboard.KEY_LEFT ||
				keyCode == Keyboard.KEY_RIGHT ||
				keyCode == Keyboard.KEY_HOME ||
				keyCode == Keyboard.KEY_END)
		{
			length.textboxKeyTyped(typedChar, keyCode);
			gateHeight.textboxKeyTyped(typedChar, keyCode);
			gateThickness.textboxKeyTyped(typedChar, keyCode);
			upperRotation.textboxKeyTyped(typedChar, keyCode);
			lowerRotation.textboxKeyTyped(typedChar, keyCode);
			speed.textboxKeyTyped(typedChar, keyCode);
			delay.textboxKeyTyped(typedChar, keyCode);
			lightStartOffset.textboxKeyTyped(typedChar, keyCode);
		}
		
		if (typedChar == '-')
		{
			upperRotation.textboxKeyTyped(typedChar, keyCode);
			lowerRotation.textboxKeyTyped(typedChar, keyCode);
		}
		
		// Check decimals
		if (typedChar == '.')
		{
			keyTypedDecimal(length, typedChar, keyCode);
			keyTypedDecimal(gateHeight, typedChar, keyCode);
			keyTypedDecimal(gateThickness, typedChar, keyCode);
			keyTypedDecimal(upperRotation, typedChar, keyCode);
			keyTypedDecimal(lowerRotation, typedChar, keyCode);
			keyTypedDecimal(speed, typedChar, keyCode);
			keyTypedDecimal(delay, typedChar, keyCode);
			keyTypedDecimal(lightStartOffset, typedChar, keyCode);
		}
		
		updateRangeColors();
	}
	
	/** Values outside their range are shown in red (they will be brought back into range when saved) */
	private void updateRangeColors()
	{
		updateRangeColor(gateHeight, CrossingGateGateTileEntity.MIN_GATE_HEIGHT, CrossingGateGateTileEntity.MAX_GATE_HEIGHT);
		updateRangeColor(gateThickness, CrossingGateGateTileEntity.MIN_GATE_THICKNESS, CrossingGateGateTileEntity.MAX_GATE_THICKNESS);
		updateRangeColor(speed, CrossingGateGateTileEntity.MIN_GATE_SPEED, CrossingGateGateTileEntity.MAX_GATE_SPEED);
	}
	
	private void updateRangeColor(GuiTextField textBox, float min, float max)
	{
		float value = parseFloatOrDefault(textBox.getText(), Float.NaN);
		textBox.setTextColor(value >= min && value <= max ? VALID_COLOR : INVALID_COLOR);
	}
	
	private void keyTypedDecimal(GuiTextField textBox, char typedChar, int keyCode)
	{
		if (!textBox.getText().contains("."))
		{
			textBox.textboxKeyTyped(typedChar, keyCode);
		}
	}
	
	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
		super.mouseClicked(mouseX, mouseY, mouseButton);
		length.mouseClicked(mouseX, mouseY, mouseButton);
		gateHeight.mouseClicked(mouseX, mouseY, mouseButton);
		gateThickness.mouseClicked(mouseX, mouseY, mouseButton);
		upperRotation.mouseClicked(mouseX, mouseY, mouseButton);
		lowerRotation.mouseClicked(mouseX, mouseY, mouseButton);
		speed.mouseClicked(mouseX, mouseY, mouseButton);
		delay.mouseClicked(mouseX, mouseY, mouseButton);
		lightStartOffset.mouseClicked(mouseX, mouseY, mouseButton);
	}
	
	@Override
	public void updateScreen() {
		super.updateScreen();

		// Close the menu as soon as the player is no longer in creative mode
		if (!ConfigPermissions.canConfigure(mc.player))
		{
			mc.displayGuiScreen(null);
		}
	}

	@Override
	public void onGuiClosed() {
		// Not in creative mode anymore: discard the changes (the server would reject them anyway)
		if (!ConfigPermissions.canConfigure(mc.player))
		{
			return;
		}

		te.setCrossingGateLength(parseFloatOrDefault(length.getText()));
		te.setUpperRotationLimit(parseFloatOrDefault(upperRotation.getText()));
		te.setLowerRotationLimit(parseFloatOrDefault(lowerRotation.getText()));
		te.setDelay(parseFloatOrDefault(delay.getText()));
		te.setLightStartOffset(parseFloatOrDefault(lightStartOffset.getText()));
		te.setGateLightCount(threeLights.isChecked() ? GateLightCount.ThreeLights : GateLightCount.OneLight);
		// Unreadable values keep the current setting, out of range values are clamped by the setters
		te.setGateHeight(parseFloatOrDefault(gateHeight.getText(), te.getGateHeight()));
		te.setGateThickness(parseFloatOrDefault(gateThickness.getText(), te.getGateThickness()));
		te.setGateSpeed(parseFloatOrDefault(speed.getText(), te.getGateSpeed()));
		te.performClientToServerSync();
	}
	
	private float parseFloatOrDefault(String text)
	{
		return parseFloatOrDefault(text, 0F);
	}
	
	private float parseFloatOrDefault(String text, float defaultValue)
	{
		try
		{
			return Float.parseFloat(text);
		}
		catch (Exception ex)
		{
			return defaultValue;
		}
	}
	
	@Override
	protected void actionPerformed(GuiButton button) throws IOException {
		super.actionPerformed(button);
		
		if (button == threeLights)
		{
			oneLight.setIsChecked(!threeLights.isChecked());
		}
		
		if (button == oneLight)
		{
			threeLights.setIsChecked(!oneLight.isChecked());
		}
	}
	
	public static class COMP_IDS
	{
		public static final int LENGTH = 1;
		public static final int UPPER_ROTATION = 2;
		public static final int LOWER_ROTATION = 3;
		public static final int DELAY = 4;
		public static final int LIGHT_START_OFFSET = 5;
		public static final int THREE_LIGHT = 6;
		public static final int ONE_LIGHT = 7;
		public static final int GATE_HEIGHT = 8;
		public static final int GATE_THICKNESS = 9;
		public static final int SPEED = 10;
	}
}

package org.Nysxl.Utils;

import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.ChatColor;

public class TextComponentBuilder {

    private final TextComponent mainComponent;

    public TextComponentBuilder() {
        this.mainComponent = new TextComponent();
    }

    /**
     * Static factory method to create a new TextComponentBuilder.
     *
     * @return A new TextComponentBuilder instance.
     */
    public static TextComponentBuilder create() {
        return new TextComponentBuilder();
    }

    /**
     * Adds plain text to the main component.
     *
     * @param text The plain text to add.
     * @return This builder instance (for chaining).
     */
    public TextComponentBuilder addPlainText(String text) {
        TextComponent part = new TextComponent(ChatColor.translateAlternateColorCodes('&', text));
        this.mainComponent.addExtra(part);
        return this;
    }

    /**
     * Adds a styled text part to the main TextComponent.
     *
     * @param text  The text to add.
     * @param color The color of the text.
     * @return This builder instance (for chaining).
     */
    public TextComponentBuilder addStyledText(String text, ChatColor color) {
        TextComponent part = new TextComponent(ChatColor.translateAlternateColorCodes('&', text));
        part.setColor(color.asBungee());
        this.mainComponent.addExtra(part);
        return this;
    }

    /**
     * Sets bold, italic, and underlined styles for the last added text part.
     *
     * @param bold      Whether the text is bold.
     * @param italic    Whether the text is italic.
     * @param underline Whether the text is underlined.
     * @return This builder instance (for chaining).
     */
    public TextComponentBuilder setStyle(boolean bold, boolean italic, boolean underline) {
        if (this.mainComponent.getExtra() != null && !this.mainComponent.getExtra().isEmpty()) {
            TextComponent lastPart = (TextComponent) this.mainComponent.getExtra().get(this.mainComponent.getExtra().size() - 1);
            lastPart.setBold(bold);
            lastPart.setItalic(italic);
            lastPart.setUnderlined(underline);
        }
        return this;
    }

    /**
     * Adds a click action to the last added text part.
     *
     * @param clickAction The ClickEvent.Action (e.g., OPEN_URL, RUN_COMMAND).
     * @param clickValue  The value for the click action (e.g., URL or command).
     * @return This builder instance (for chaining).
     */
    public TextComponentBuilder setClickAction(ClickEvent.Action clickAction, String clickValue) {
        if (clickAction != null && clickValue != null) {
            if (this.mainComponent.getExtra() != null && !this.mainComponent.getExtra().isEmpty()) {
                TextComponent lastPart = (TextComponent) this.mainComponent.getExtra().get(this.mainComponent.getExtra().size() - 1);
                lastPart.setClickEvent(new ClickEvent(clickAction, clickValue));
            }
        }
        return this;
    }

    /**
     * Adds a hover action to the last added text part.
     *
     * @param hoverText The text to display on hover.
     * @return This builder instance (for chaining).
     */
    public TextComponentBuilder setHoverAction(String hoverText) {
        if (hoverText != null) {
            if (this.mainComponent.getExtra() != null && !this.mainComponent.getExtra().isEmpty()) {
                TextComponent lastPart = (TextComponent) this.mainComponent.getExtra().get(this.mainComponent.getExtra().size() - 1);
                lastPart.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder(ChatColor.translateAlternateColorCodes('&', hoverText)).create()));
            }
        }
        return this;
    }

    /**
     * Builds the complete TextComponent.
     *
     * @return The final TextComponent.
     */
    public TextComponent build() {
        return this.mainComponent;
    }
}

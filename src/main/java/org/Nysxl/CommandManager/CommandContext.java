package org.Nysxl.CommandManager;

import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;

public class CommandContext {
    private final CommandSender sender;
    private final String label;
    private final List<String> args;
    private final Map<String, Object> additionalData;

    public CommandContext(CommandSender sender, String label, List<String> args, Map<String, Object> additionalData) {
        this.sender = sender;
        this.label = label;
        this.args = args;
        this.additionalData = additionalData;
    }

    public CommandSender getSender() {
        return sender;
    }

    public String getLabel() {
        return label;
    }

    public List<String> getArgs() {
        return args;
    }

    public Map<String, Object> getAdditionalData() {
        return additionalData;
    }
}
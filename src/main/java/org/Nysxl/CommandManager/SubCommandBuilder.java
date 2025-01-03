package org.Nysxl.CommandManager;

import org.Nysxl.CommandManager.CommandBuilder;

class SubCommandBuilder extends CommandBuilder {
    private final CommandBuilder parentBuilder;

    public SubCommandBuilder(CommandBuilder parentBuilder) {
        this.parentBuilder = parentBuilder;
    }

    public CommandBuilder endSubCommand() {
        parentBuilder.addSubCommand(this);
        return parentBuilder;
    }

    public String getName() {
        return super.getName();
    }
}
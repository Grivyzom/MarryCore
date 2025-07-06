package gc.grivyzom.marryCore.commands;

import gc.grivyzom.marryCore.MarryCore;
import gc.grivyzom.marryCore.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Comando para aceptar propuestas de matrimonio.
 *
 * @author Brocolitx
 * @version 0.0.1
 */
public class AcceptCommand implements CommandExecutor {

    private final MarryCore plugin;
    private final MessageUtils messageUtils;

    public AcceptCommand(MarryCore plugin) {
        this.plugin = plugin;
        this.messageUtils = new MessageUtils(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Solo jugadores pueden usar este comando
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
            return true;
        }

        Player player = (Player) sender;

        // Verificar permisos
        if (!player.hasPermission("marrycore.marry")) {
            messageUtils.sendMessage(player, "general.no-permission");
            return true;
        }

        // Intentar aceptar la propuesta
        boolean success = MarryCommand.acceptProposal(player);

        if (!success) {
            messageUtils.sendMessage(player, "marriage.accept.no-proposal");
        }

        return true;
    }
}
package gc.grivyzom.marryCore.commands;

import gc.grivyzom.marryCore.MarryCore;
import gc.grivyzom.marryCore.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Comando para aceptar propuestas de matrimonio y noviazgo.
 * ACTUALIZADO: Ahora maneja ambos tipos de propuestas.
 *
 * @author Brocolitx
 * @version 0.1.0
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
        if (!player.hasPermission("marrycore.marry") && !player.hasPermission("marrycore.dating")) {
            messageUtils.sendMessage(player, "general.no-permission");
            return true;
        }

        // ACTUALIZADO: Intentar aceptar propuesta de noviazgo primero
        boolean datingSuccess = DatingCommand.acceptDatingProposal(player, plugin);
        if (datingSuccess) {
            return true; // Se aceptó una propuesta de noviazgo
        }

        // Si no había propuesta de noviazgo, intentar propuesta de matrimonio
        boolean marriageSuccess = MarryCommand.acceptProposal(player);
        if (marriageSuccess) {
            return true; // Se aceptó una propuesta de matrimonio/compromiso
        }

        // Si no había ninguna propuesta pendiente
        messageUtils.sendMessage(player, "marriage.accept.no-proposal");
        return true;
    }
}
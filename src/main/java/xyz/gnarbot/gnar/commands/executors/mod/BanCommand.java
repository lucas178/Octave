package xyz.gnarbot.gnar.commands.executors.mod;

import net.dv8tion.jda.core.Permission;
import xyz.gnarbot.gnar.commands.handlers.Command;
import xyz.gnarbot.gnar.commands.handlers.CommandExecutor;
import xyz.gnarbot.gnar.members.Client;
import xyz.gnarbot.gnar.members.Level;
import xyz.gnarbot.gnar.utils.Note;

import java.util.List;

@Command(aliases = "ban",
        level = Level.BOT_COMMANDER,
        guildPermissions = Permission.BAN_MEMBERS)
public class BanCommand extends CommandExecutor {
    @Override
    public void execute(Note note, List<String> args) {
        Client author = note.getAuthor();
        Client target = null;

//        if (!author.hasPermission(note.getTextChannel(), Permission.BAN_MEMBERS)) {
//            note.respond().error("You do not have permission to ban.").queue();
//            return;
//        }

        if (note.getMentionedChannels().size() >= 1) {
            target = note.getMentionedUsers().get(0);
        } else if (args.size() >= 1) {
            target = note.getServlet().getClientHandler().getClientByName(args.get(0), false);
        }

        if (target == null) {
            note.respond().error("Could not find user.").queue();
            return;
        }
        if (!author.canInteract(target)) {
            note.respond().error("Sorry, that user has an equal or higher role.").queue();
            return;
        }

        getServlet().getController().ban(target, 2).queue();
        note.respond().info(target.getEffectiveName() + " has been banned.").queue();
    }
}
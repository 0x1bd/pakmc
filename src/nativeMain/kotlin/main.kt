import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.completion.CompletionCommand
import com.github.ajalt.clikt.core.main
import org.kvxd.pakmc.commands.AddCommand
import org.kvxd.pakmc.commands.BuildCommand
import org.kvxd.pakmc.commands.InitCommand

class PakMCCommand : CliktCommand(name = "pakmc") {

    override fun help(context: Context): String = "Minecraft modpack helper"
    override fun run() {}
}

fun main(args: Array<String>) {
    PakMCCommand()
        .subcommands(
            InitCommand(),
            AddCommand(),
            BuildCommand(),
            CompletionCommand()
        )
        .main(args)
}
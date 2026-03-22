package com.joz.cli;

import com.joz.cli.command.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/** JOz CLI entry point — Spring Boot + picocli. */
@SpringBootApplication
@ComponentScan(basePackages = "com.joz")
@Command(name = "joz",
        version = "JOz 0.1.0",
        description = "AI coding agent for your terminal",
        mixinStandardHelpOptions = true,
        subcommands = {
                RunCommand.class,
                PlanCommand.class,
                SkillCommand.class,
                InitCommand.class,
                ConfigCommand.class,
                SessionCommand.class
        })
public class JozCli implements CommandLineRunner {

    private final CommandLine.IFactory factory;

    public JozCli(CommandLine.IFactory factory) {
        this.factory = factory;
    }

    @Override
    public void run(String... args) {
        var commandLine = new CommandLine(this, factory);
        commandLine.setExecutionStrategy(new CommandLine.RunLast());

        // If no args, default to 'run' command (interactive REPL)
        if (args.length == 0) {
            args = new String[]{"run"};
        }

        int exitCode = commandLine.execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    public static void main(String[] args) {
        System.setProperty("spring.main.banner-mode", "off");
        System.setProperty("spring.main.web-application-type", "none");
        System.setProperty("logging.level.root", "WARN");
        System.setProperty("logging.level.com.joz", "INFO");
        SpringApplication.run(JozCli.class, args);
    }
}

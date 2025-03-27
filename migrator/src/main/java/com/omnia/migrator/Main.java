package com.omnia.migrator;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.omnia.common.config.AppConfig;
import com.omnia.common.config.YamlParser;

import java.io.FileInputStream;
import java.util.List;

public class Main {
    static Arguments arguments = new Arguments();
    private static MigratorCommand command = null;

    private static void parseCli(String[] args) {
        JCommander jCommander = JCommander.newBuilder()
                .addObject(arguments)
                .addCommand(InfoCommand.name, arguments.infoCommand)
                .addCommand(MigrateCommand.name, arguments.migrateCommand)
                .addCommand(MigrateAllCommand.name, arguments.migrateAllCommand)
                .build();

        jCommander.parse(args);

        if (arguments.help) {
            jCommander.usage();
            System.exit(0);
        }

        String parsedCommand = jCommander.getParsedCommand();


        switch (parsedCommand) {
            case InfoCommand.name:
                command = arguments.infoCommand;
                break;
            case MigrateCommand.name:
                command = arguments.migrateCommand;
                break;
            case MigrateAllCommand.name:
                command = arguments.migrateAllCommand;
                break;
            default:
                System.err.println("Unknown command: " + parsedCommand);
                System.exit(1);
        }
    }

    private static AppConfig parseConfig(String configFileName) {
        try (FileInputStream configFile = new FileInputStream(configFileName)) {
            return YamlParser.parseYaml(configFile);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        return null;
    }


    public static void main(String[] args) {
        parseCli(args);
        AppConfig config = parseConfig(arguments.configFile);

        assert command != null;
        try (Migrator migrator = Migrator.getInstance(config)) {
            command.execute(migrator);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    public interface MigratorCommand {
        void execute(Migrator migrator) throws Exception;
    }

    @Parameters(commandDescription = "Main command")
    public static class Arguments {
        public InfoCommand infoCommand = new InfoCommand();
        public MigrateCommand migrateCommand = new MigrateCommand();
        public MigrateAllCommand migrateAllCommand = new MigrateAllCommand();

        @Parameter(names = {"--config", "-c"}, description = "Config file path for app")
        String configFile;

        @Parameter(names = "--help", help = true)
        private final boolean help = true;
    }

    @Parameters(commandDescription = "Show info about Migrator state")
    public static class InfoCommand implements MigratorCommand {
        static public final String name = "info";

        @Override
        public void execute(Migrator migrator) throws Exception {
            List<CommuneId> communeIds = migrator.scan();
            long communesSize = migrator.countCommunes();

            System.out.println("Successfully connected to OpenSearch and Postgresql");
            System.out.printf("Communes (overflown/total): %d/%d\n", communeIds.size(), communesSize);
        }
    }

    @Parameters(commandDescription = "Migrate overflown from given communes")
    public static class MigrateCommand implements MigratorCommand {
        static public final String name = "migrate";

        @Parameter(description = "Commune migration ids")
        public List<String> migrateIds;

        @Override
        public void execute(Migrator migrator) throws Exception {
            for (String migrateId : migrateIds) {
                migrator.migrate(CommuneId.fromString(migrateId));
            }
        }
    }

    @Parameters(commandDescription = "Migrate all overflown communes")
    public static class MigrateAllCommand implements MigratorCommand {
        static public final String name = "migrate-all";

        public void execute(Migrator migrator) throws Exception {
            migrator.migrateAll();
        }
    }
}

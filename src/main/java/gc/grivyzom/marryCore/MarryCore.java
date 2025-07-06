package gc.grivyzom.marryCore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class MarryCore extends JavaPlugin {

    private FileConfiguration databaseConfig;
    private Connection connection;

    @Override
    public void onEnable() {
        // Mensaje de inicio
        getLogger().info(ChatColor.GREEN + "=================================");
        getLogger().info(ChatColor.GREEN + "  MarryCore v0.0.1 - Iniciando...");
        getLogger().info(ChatColor.GREEN + "  Autor: Brocolitx");
        getLogger().info(ChatColor.GREEN + "  Website: www.grivyzom.com");
        getLogger().info(ChatColor.GREEN + "=================================");

        // Crear archivos de configuración
        createDatabaseConfig();

        // Cargar configuración de base de datos
        loadDatabaseConfig();

        // Conectar a la base de datos
        connectToDatabase();

        // Crear tablas si no existen
        createTables();

        getLogger().info(ChatColor.GREEN + "¡MarryCore ha sido habilitado correctamente!");
    }

    @Override
    public void onDisable() {
        // Mensaje de apagado
        getLogger().info(ChatColor.RED + "=================================");
        getLogger().info(ChatColor.RED + "  MarryCore v0.0.1 - Deshabilitando...");
        getLogger().info(ChatColor.RED + "  Autor: Brocolitx");
        getLogger().info(ChatColor.RED + "  ¡Gracias por usar MarryCore!");
        getLogger().info(ChatColor.RED + "=================================");

        // Cerrar conexión a la base de datos
        if (connection != null) {
            try {
                connection.close();
                getLogger().info(ChatColor.YELLOW + "Conexión a la base de datos cerrada.");
            } catch (SQLException e) {
                getLogger().warning("Error al cerrar la conexión a la base de datos: " + e.getMessage());
            }
        }

        getLogger().info(ChatColor.RED + "¡MarryCore ha sido deshabilitado!");
    }

    private void createDatabaseConfig() {
        File databaseFile = new File(getDataFolder(), "database.yml");

        if (!databaseFile.exists()) {
            try {
                // Crear directorio si no existe
                if (!getDataFolder().exists()) {
                    getDataFolder().mkdirs();
                }

                // Crear archivo
                databaseFile.createNewFile();

                // Configurar valores por defecto
                FileConfiguration config = YamlConfiguration.loadConfiguration(databaseFile);
                config.set("database.host", "localhost");
                config.set("database.port", 3306);
                config.set("database.name", "marrycore");
                config.set("database.username", "root");
                config.set("database.password", "");
                config.set("database.useSSL", false);
                config.set("database.autoReconnect", true);

                // Guardar configuración
                config.save(databaseFile);

                getLogger().info(ChatColor.YELLOW + "Archivo database.yml creado. Configure sus datos de conexión.");

            } catch (IOException e) {
                getLogger().severe("Error al crear el archivo database.yml: " + e.getMessage());
            }
        }
    }

    private void loadDatabaseConfig() {
        File databaseFile = new File(getDataFolder(), "database.yml");
        databaseConfig = YamlConfiguration.loadConfiguration(databaseFile);
        getLogger().info(ChatColor.GREEN + "Configuración de base de datos cargada.");
    }

    private void connectToDatabase() {
        String host = databaseConfig.getString("database.host");
        int port = databaseConfig.getInt("database.port");
        String database = databaseConfig.getString("database.name");
        String username = databaseConfig.getString("database.username");
        String password = databaseConfig.getString("database.password");
        boolean useSSL = databaseConfig.getBoolean("database.useSSL");
        boolean autoReconnect = databaseConfig.getBoolean("database.autoReconnect");

        try {
            // Construir URL de conexión
            String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=%s&autoReconnect=%s",
                    host, port, database, useSSL, autoReconnect);

            // Establecer conexión
            connection = DriverManager.getConnection(url, username, password);

            getLogger().info(ChatColor.GREEN + "¡Conexión exitosa a la base de datos MySQL!");

        } catch (SQLException e) {
            getLogger().severe("Error al conectar con la base de datos: " + e.getMessage());
            getLogger().severe("Verifique la configuración en database.yml");

            // Deshabilitar plugin si no se puede conectar
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    private void createTables() {
        if (connection == null) {
            getLogger().warning("No se puede crear tablas: conexión nula");
            return;
        }

        try {
            // Tabla de jugadores
            String playersTable = """
                CREATE TABLE IF NOT EXISTS marry_players (
                    uuid VARCHAR(36) PRIMARY KEY,
                    username VARCHAR(16) NOT NULL,
                    status ENUM('soltero', 'comprometido', 'casado') DEFAULT 'soltero',
                    partner_uuid VARCHAR(36) DEFAULT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    FOREIGN KEY (partner_uuid) REFERENCES marry_players(uuid) ON DELETE SET NULL
                )
            """;

            // Tabla de matrimonios
            String marriagesTable = """
                CREATE TABLE IF NOT EXISTS marry_marriages (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    player1_uuid VARCHAR(36) NOT NULL,
                    player2_uuid VARCHAR(36) NOT NULL,
                    status ENUM('comprometido', 'casado', 'divorciado') DEFAULT 'comprometido',
                    engagement_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    wedding_date TIMESTAMP NULL,
                    ceremony_location VARCHAR(100) DEFAULT NULL,
                    divorce_date TIMESTAMP NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    FOREIGN KEY (player1_uuid) REFERENCES marry_players(uuid) ON DELETE CASCADE,
                    FOREIGN KEY (player2_uuid) REFERENCES marry_players(uuid) ON DELETE CASCADE
                )
            """;

            // Tabla de invitados
            String guestsTable = """
                CREATE TABLE IF NOT EXISTS marry_guests (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    marriage_id INT NOT NULL,
                    guest_uuid VARCHAR(36) NOT NULL,
                    invited_by VARCHAR(36) NOT NULL,
                    status ENUM('invitado', 'confirmado', 'rechazado') DEFAULT 'invitado',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (marriage_id) REFERENCES marry_marriages(id) ON DELETE CASCADE,
                    FOREIGN KEY (guest_uuid) REFERENCES marry_players(uuid) ON DELETE CASCADE,
                    FOREIGN KEY (invited_by) REFERENCES marry_players(uuid) ON DELETE CASCADE
                )
            """;

            // Ejecutar creación de tablas
            connection.createStatement().execute(playersTable);
            connection.createStatement().execute(marriagesTable);
            connection.createStatement().execute(guestsTable);

            getLogger().info(ChatColor.GREEN + "Tablas de base de datos creadas/verificadas correctamente.");

        } catch (SQLException e) {
            getLogger().severe("Error al crear tablas: " + e.getMessage());
        }
    }

    // Getter para la conexión (para uso en otras clases)
    public Connection getConnection() {
        return connection;
    }

    // Getter para la configuración de database
    public FileConfiguration getDatabaseConfig() {
        return databaseConfig;
    }
}
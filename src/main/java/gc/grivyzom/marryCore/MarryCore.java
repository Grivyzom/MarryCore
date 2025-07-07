package gc.grivyzom.marryCore;

import gc.grivyzom.marryCore.commands.*;
import gc.grivyzom.marryCore.database.DatabaseManager;
import gc.grivyzom.marryCore.listeners.*;
import gc.grivyzom.marryCore.placeholders.PlaceholderManager;
import gc.grivyzom.marryCore.utils.MessageUtils;
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
    private DatabaseManager databaseManager;
    private MessageUtils messageUtils;
    private PlaceholderManager placeholderManager;

    @Override
    public void onEnable() {
        // Mensaje de inicio
        getLogger().info(ChatColor.GREEN + "=================================");
        getLogger().info(ChatColor.GREEN + "  MarryCore v0.0.1 - Iniciando...");
        getLogger().info(ChatColor.GREEN + "  Autor: Brocolitx");
        getLogger().info(ChatColor.GREEN + "  Website: www.grivyzom.com");
        getLogger().info(ChatColor.GREEN + "=================================");

        try {
            // Crear configuraciones
            saveDefaultConfig();
            createDatabaseConfig();

            // Crear archivos de recursos
            saveResource("messages.yml", false);
            saveResource("items.yml", false);

            // Cargar configuración de base de datos
            loadDatabaseConfig();

            // Conectar a la base de datos
            if (!connectToDatabase()) {
                getLogger().severe("No se pudo conectar a la base de datos. Deshabilitando plugin...");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            // Crear tablas si no existen
            if (!createTables()) {
                getLogger().severe("No se pudieron crear las tablas. Deshabilitando plugin...");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            // Inicializar managers
            initializeManagers();

            // Inicializar sistema de placeholders
            initializePlaceholders();

            // Registrar comandos
            registerCommands();

            // Registrar listeners
            registerListeners();

            // Tareas programadas
            scheduleRepeatingTasks();

            getLogger().info(ChatColor.GREEN + "¡MarryCore ha sido habilitado correctamente!");

        } catch (Exception e) {
            getLogger().severe("Error crítico durante la inicialización: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        // Mensaje de apagado
        getLogger().info(ChatColor.RED + "=================================");
        getLogger().info(ChatColor.RED + "  MarryCore v0.0.1 - Deshabilitando...");
        getLogger().info(ChatColor.RED + "  Autor: Brocolitx");
        getLogger().info(ChatColor.RED + "  ¡Gracias por usar MarryCore!");
        getLogger().info(ChatColor.RED + "=================================");

        // Deshabilitar placeholders
        if (placeholderManager != null) {
            placeholderManager.disable();
        }

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

    private boolean connectToDatabase() {
        String host = databaseConfig.getString("database.host", "localhost");
        int port = databaseConfig.getInt("database.port", 3306);
        String database = databaseConfig.getString("database.name", "marrycore");
        String username = databaseConfig.getString("database.username", "root");
        String password = databaseConfig.getString("database.password", "");
        boolean useSSL = databaseConfig.getBoolean("database.useSSL", false);
        boolean autoReconnect = databaseConfig.getBoolean("database.autoReconnect", true);

        try {
            // Cargar driver de MySQL explícitamente
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Construir URL de conexión con parámetros adicionales
            String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=%s&autoReconnect=%s&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC",
                    host, port, database, useSSL, autoReconnect);

            getLogger().info("Intentando conectar a la base de datos...");
            getLogger().info("URL de conexión: " + url.replaceAll("password=[^&]+", "password=***"));

            // Establecer conexión
            connection = DriverManager.getConnection(url, username, password);

            // Verificar que la conexión esté activa
            if (connection != null && !connection.isClosed()) {
                getLogger().info(ChatColor.GREEN + "¡Conexión exitosa a la base de datos MySQL!");
                return true;
            } else {
                getLogger().severe("La conexión a la base de datos es nula o está cerrada");
                return false;
            }

        } catch (ClassNotFoundException e) {
            getLogger().severe("Driver MySQL no encontrado. Asegúrese de que mysql-connector-java esté en el classpath.");
            getLogger().severe("Error: " + e.getMessage());
            return false;
        } catch (SQLException e) {
            getLogger().severe("Error al conectar con la base de datos MySQL:");
            getLogger().severe("Código de error: " + e.getErrorCode());
            getLogger().severe("Estado SQL: " + e.getSQLState());
            getLogger().severe("Mensaje: " + e.getMessage());
            getLogger().severe("");
            getLogger().severe("Posibles soluciones:");
            getLogger().severe("1. Verifique que MySQL esté ejecutándose");
            getLogger().severe("2. Compruebe las credenciales en database.yml");
            getLogger().severe("3. Asegúrese de que la base de datos '" + database + "' exista");
            getLogger().severe("4. Verifique que el usuario tenga permisos");
            return false;
        } catch (Exception e) {
            getLogger().severe("Error inesperado al conectar a la base de datos: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean createTables() {
        if (connection == null) {
            getLogger().warning("No se puede crear tablas: conexión nula");
            return false;
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
                    INDEX idx_partner (partner_uuid),
                    INDEX idx_status (status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
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
                    INDEX idx_players (player1_uuid, player2_uuid),
                    INDEX idx_status (status),
                    INDEX idx_wedding_date (wedding_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
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
                    INDEX idx_marriage (marriage_id),
                    INDEX idx_guest (guest_uuid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

            // Ejecutar creación de tablas
            connection.createStatement().execute(playersTable);
            getLogger().info("Tabla marry_players creada/verificada");

            connection.createStatement().execute(marriagesTable);
            getLogger().info("Tabla marry_marriages creada/verificada");

            connection.createStatement().execute(guestsTable);
            getLogger().info("Tabla marry_guests creada/verificada");

            getLogger().info(ChatColor.GREEN + "Tablas de base de datos creadas/verificadas correctamente.");
            return true;

        } catch (SQLException e) {
            getLogger().severe("Error al crear tablas: " + e.getMessage());
            getLogger().severe("Código de error: " + e.getErrorCode());
            getLogger().severe("Estado SQL: " + e.getSQLState());
            e.printStackTrace();
            return false;
        }
    }

    private void initializeManagers() {
        try {
            this.databaseManager = new DatabaseManager(this);
            this.messageUtils = new MessageUtils(this);
            getLogger().info("Managers inicializados correctamente");
        } catch (Exception e) {
            getLogger().severe("Error al inicializar managers: " + e.getMessage());
            throw e;
        }
    }

    private void initializePlaceholders() {
        try {
            this.placeholderManager = new PlaceholderManager(this);
            getLogger().info("Sistema de placeholders inicializado");

            if (placeholderManager.isPlaceholderAPIEnabled()) {
                getLogger().info(ChatColor.GREEN + "PlaceholderAPI detectado - Placeholders habilitados");
            } else {
                getLogger().info(ChatColor.YELLOW + "PlaceholderAPI no detectado - Funcionalidad básica disponible");
            }
        } catch (Exception e) {
            getLogger().warning("Error al inicializar placeholders: " + e.getMessage());
            // No es crítico, continuar sin placeholders
        }
    }

// ACTUALIZAR el método registerCommands() en MarryCore.java

    private void registerCommands() {
        try {
            // NUEVO: Registrar comando de noviazgo
            if (getCommand("novio") != null) {
                getCommand("novio").setExecutor(new DatingCommand(this));
            } else {
                getLogger().warning("Comando 'novio' no encontrado en plugin.yml");
            }

            // Verificar que los comandos estén definidos en plugin.yml
            if (getCommand("marry") != null) {
                getCommand("marry").setExecutor(new MarryCommand(this));
            } else {
                getLogger().warning("Comando 'marry' no encontrado en plugin.yml");
            }

            if (getCommand("aceptar") != null) {
                getCommand("aceptar").setExecutor(new AcceptCommand(this));
            }

            if (getCommand("rechazar") != null) {
                getCommand("rechazar").setExecutor(new RejectCommand(this));
            }

            if (getCommand("casamiento") != null) {
                getCommand("casamiento").setExecutor(new WeddingCommand(this));
            }

            if (getCommand("invitados") != null) {
                getCommand("invitados").setExecutor(new GuestsCommand(this));
            }

            if (getCommand("divorcio") != null) {
                getCommand("divorcio").setExecutor(new DivorceCommand(this));
            }

            if (getCommand("conyuge") != null) {
                getCommand("conyuge").setExecutor(new SpouseTeleportCommand(this));
            }

            if (getCommand("marrycore") != null) {
                getCommand("marrycore").setExecutor(new AdminCommand(this));
            } else {
                getLogger().warning("Comando 'marrycore' no encontrado en plugin.yml");
            }

            getLogger().info("Comandos registrados correctamente (incluido comando de noviazgo)");
        } catch (Exception e) {
            getLogger().severe("Error al registrar comandos: " + e.getMessage());
            throw e;
        }
    }

    private void registerListeners() {
        try {
            getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
            getServer().getPluginManager().registerEvents(new ChatListener(this), this);
            getServer().getPluginManager().registerEvents(new PlayerInteractListener(this), this);
            getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
            getServer().getPluginManager().registerEvents(new KissListener(this), this); // NUEVO LISTENER AÑADIDO
            getLogger().info("Listeners registrados correctamente (incluido KissListener)");
        } catch (Exception e) {
            getLogger().severe("Error al registrar listeners: " + e.getMessage());
            throw e;
        }
    }

    private void scheduleRepeatingTasks() {
        try {
            // Tarea de guardado automático cada 5 minutos
            int saveInterval = getConfig().getInt("general.auto_save_interval", 5) * 60 * 20; // Convertir a ticks
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                // Aquí puedes añadir lógica de guardado automático
                if (getConfig().getBoolean("general.debug", false)) {
                    getLogger().info("Ejecutando guardado automático...");
                }
            }, saveInterval, saveInterval);

            // Verificar recordatorios de bodas cada hora
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                // Verificar bodas próximas y enviar recordatorios
                checkUpcomingWeddings();
            }, 20 * 60 * 60, 20 * 60 * 60); // Cada hora

            getLogger().info("Tareas programadas iniciadas correctamente");
        } catch (Exception e) {
            getLogger().severe("Error al programar tareas: " + e.getMessage());
            throw e;
        }
    }

    private void checkUpcomingWeddings() {
        // TODO: Implementar verificación de bodas próximas
        // y envío de recordatorios a los jugadores
    }

    // Getters para uso en otras clases
    public Connection getConnection() {
        return connection;
    }

    public FileConfiguration getDatabaseConfig() {
        return databaseConfig;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public MessageUtils getMessageUtils() {
        return messageUtils;
    }

    public PlaceholderManager getPlaceholderManager() {
        return placeholderManager;
    }

    // Método para recargar configuraciones
    public void reloadConfigs() {
        reloadConfig();
        loadDatabaseConfig();
        if (messageUtils != null) {
            messageUtils.reloadMessages();
        }
        if (placeholderManager != null) {
            placeholderManager.reload();
        }
    }

    // Método para verificar la salud de la conexión
    public boolean isDatabaseConnected() {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(5);
        } catch (SQLException e) {
            return false;
        }
    }

    // Método para reconectar a la base de datos
    public boolean reconnectDatabase() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            getLogger().warning("Error al cerrar conexión anterior: " + e.getMessage());
        }

        return connectToDatabase();
    }
}
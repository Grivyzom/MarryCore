# MarryCore

[![Version](https://img.shields.io/badge/version-0.0.1-blue.svg)](https://github.com/Brocolitx/MarryCore)
[![Minecraft](https://img.shields.io/badge/minecraft-1.20.1-green.svg)](https://www.spigotmc.org/)
[![Java](https://img.shields.io/badge/java-17-orange.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/license-MIT-lightgrey.svg)](LICENSE)

Un sistema completo de matrimonio para servidores de Minecraft Survival que permite a los jugadores casarse, organizar ceremonias y disfrutar de beneficios únicos.

## ✨ Características Principales

### 🎯 Sistema de Matrimonio
- **Propuestas de matrimonio** con anillos especiales
- **Ceremonias personalizables** con invitados
- **Estados civiles** dinámicos (Soltero, Comprometido, Casado)
- **Sistema de divorcios** con confirmación

### 💍 Ítems Especiales
- **Anillo de Propuesta** - Para proponer matrimonio
- **Anillo Nupcial** - Obtenido al comprometerse
- **Anillo de Boda** - Con habilidades especiales de teletransporte
- **Invitaciones de Boda** - Para ceremonias oficiales

### 🎉 Beneficios del Matrimonio
- **Teletransporte al cónyuge** con cooldown configurable
- **Bonus de experiencia** cuando trabajas con tu pareja
- **Chat privado** exclusivo entre cónyuges
- **Efectos visuales** especiales y partículas
- **Misiones diarias** cooperativas (planificado)

### 🏛️ Sistema de Ceremonias
- **Programación de bodas** con fechas específicas
- **Ubicaciones predefinidas** para ceremonias
- **Sistema de invitados** con confirmación
- **Límites configurables** de invitados por ceremonia

### 🛡️ Características Técnicas
- **Base de datos MySQL** para persistencia
- **API completa** para otros plugins
- **Sistema de permisos** granular
- **Configuración extensiva** y personalizable
- **Soporte multiidioma** (ES, EN planificado)

## 📋 Requisitos

- **Minecraft:** 1.20.1+
- **Java:** 17+
- **Spigot/Paper:** 1.20.1+
- **MySQL:** 5.7+ o 8.0+

### Dependencias Opcionales
- **PlaceholderAPI** - Para placeholders personalizados
- **Vault** - Para integración económica
- **LuckPerms** - Para permisos avanzados
- **WorldGuard** - Para protección de ceremonias

## 🚀 Instalación

1. **Descargar** el archivo JAR desde [Releases](https://github.com/Brocolitx/MarryCore/releases)
2. **Colocar** en la carpeta `plugins/` de tu servidor
3. **Configurar** la base de datos en `plugins/MarryCore/database.yml`
4. **Reiniciar** el servidor
5. **Configurar** permisos según tus necesidades

### Configuración de Base de Datos

```yaml
database:
  host: "localhost"
  port: 3306
  name: "marrycore"
  username: "tu_usuario"
  password: "tu_contraseña"
  useSSL: false
  autoReconnect: true
```

## 🎮 Comandos

### Comandos de Jugador

| Comando | Descripción | Permiso |
|---------|-------------|---------|
| `/marry <jugador>` | Proponer matrimonio | `marrycore.marry` |
| `/aceptar` | Aceptar propuesta | `marrycore.marry` |
| `/rechazar` | Rechazar propuesta | `marrycore.marry` |
| `/casamiento <fecha> [hora]` | Programar ceremonia | `marrycore.wedding` |
| `/invitados <add\|remove\|list> [jugador]` | Gestionar invitados | `marrycore.guests` |
| `/divorcio` | Divorciarse | `marrycore.divorce` |
| `/conyuge` | Teletransportarse al cónyuge | `marrycore.teleport` |

### Comandos de Administración

| Comando | Descripción | Permiso |
|---------|-------------|---------|
| `/marrycore reload` | Recargar configuración | `marrycore.admin` |
| `/marrycore forceengage <p1> <p2>` | Forzar compromiso | `marrycore.admin` |
| `/marrycore forcemarry <p1> <p2>` | Forzar matrimonio | `marrycore.admin` |
| `/marrycore forcedivorce <jugador>` | Forzar divorcio | `marrycore.admin` |
| `/marrycore givering <jugador> <tipo>` | Dar anillo | `marrycore.admin` |
| `/marrycore reset <jugador>` | Resetear datos | `marrycore.admin` |
| `/marrycore stats` | Ver estadísticas | `marrycore.admin` |
| `/marrycore repair` | Reparar base de datos | `marrycore.admin` |

## 🔧 Configuración

### Archivo Principal (config.yml)

```yaml
# Configuraciones generales
general:
  language: "es"
  debug: false
  auto_save_interval: 5

# Sistema de matrimonio
marriage:
  proposal:
    timeout: 5
    max_distance: 10
    require_ring: true
    announce_engagements: true
  
  wedding:
    min_advance_days: 1
    max_advance_days: 30
    max_guests: 20
    ceremony_duration: 10

# Beneficios del matrimonio
benefits:
  teleport:
    enabled: true
    cooldown: 30
    experience_cost: 1
    warmup_time: 3
  
  private_chat:
    enabled: true
    command: "pc"
    format: "&d[♥] &f{sender}: {message}"
```

### Personalización de Ítems (items.yml)

```yaml
proposal_ring:
  material: GOLD_INGOT
  name: "&e&l⭐ Anillo de Propuesta ⭐"
  lore:
    - "&7Un anillo especial para proponer"
    - "&7matrimonio a tu ser querido."
  glow: true
  recipe:
    enabled: true
    shape:
      - " G "
      - "GDG"
      - " G "
    ingredients:
      G: GOLD_INGOT
      D: DIAMOND
```

## 🎨 Personalización

### Placeholders Disponibles

- `%marrycore_status%` - Estado civil del jugador
- `%marrycore_partner%` - Nombre del cónyuge
- `%marrycore_wedding_date%` - Fecha de la boda
- `%marrycore_days_married%` - Días casado
- `%marrycore_guests_count%` - Número de invitados confirmados

### Eventos de API

```java
// Evento cuando dos jugadores se comprometen
MarriageEngagementEvent event = new MarriageEngagementEvent(player1, player2);

// Evento cuando se completa una ceremonia
WeddingCompletedEvent event = new WeddingCompletedEvent(couple, location);

// Evento cuando una pareja se divorcia
DivorceEvent event = new DivorceEvent(player1, player2, reason);
```

## 🔐 Permisos

### Permisos Básicos
- `marrycore.marry` - Proponer y aceptar matrimonios
- `marrycore.wedding` - Programar ceremonias
- `marrycore.guests` - Gestionar invitados
- `marrycore.divorce` - Divorciarse
- `marrycore.teleport` - Teletransportarse al cónyuge

### Permisos de Administración
- `marrycore.admin.*` - Todos los permisos administrativos
- `marrycore.admin.force` - Forzar matrimonios/divorcios
- `marrycore.admin.give` - Dar ítems especiales
- `marrycore.admin.reset` - Resetear datos de jugadores

### Permisos de Bypass
- `marrycore.bypass.cooldown` - Saltarse cooldowns
- `marrycore.bypass.distance` - Proponer sin restricción de distancia
- `marrycore.bypass.limits` - Saltarse límites diarios

## 📊 Base de Datos

### Estructura de Tablas

**marry_players**
- `uuid` (VARCHAR) - UUID del jugador
- `username` (VARCHAR) - Nombre del jugador
- `status` (ENUM) - Estado civil
- `partner_uuid` (VARCHAR) - UUID de la pareja
- `created_at`, `updated_at` (TIMESTAMP)

**marry_marriages**
- `id` (INT) - ID único del matrimonio
- `player1_uuid`, `player2_uuid` (VARCHAR) - UUIDs de la pareja
- `status` (ENUM) - Estado del matrimonio
- `engagement_date`, `wedding_date`, `divorce_date` (TIMESTAMP)
- `ceremony_location` (VARCHAR)

**marry_guests**
- `id` (INT) - ID único del invitado
- `marriage_id` (INT) - ID del matrimonio
- `guest_uuid` (VARCHAR) - UUID del invitado
- `invited_by` (VARCHAR) - UUID de quien invita
- `status` (ENUM) - Estado de la invitación

## 🐛 Problemas Conocidos

- [ ] El sistema de misiones diarias está en desarrollo
- [ ] La integración con WorldGuard requiere configuración manual
- [ ] Los efectos de partículas pueden causar lag en servidores grandes

## 🤝 Contribuir

¡Las contribuciones son bienvenidas! Por favor:

1. Fork el proyecto
2. Crea una rama para tu feature (`git checkout -b feature/AmazingFeature`)
3. Commit tus cambios (`git commit -m 'Add some AmazingFeature'`)
4. Push a la rama (`git push origin feature/AmazingFeature`)
5. Abre un Pull Request

## 📄 Licencia

Este proyecto está bajo la Licencia MIT. Ver `LICENSE` para más detalles.

## 👥 Autor

- **Brocolitx** - *Desarrollo inicial* - [GitHub](https://github.com/Brocolitx)

## 🙏 Agradecimientos

- A la comunidad de Spigot por sus recursos
- A los beta testers que ayudaron a mejorar el plugin
- A todos los que contribuyeron con ideas y feedback

## 📞 Soporte

- **Website:** [www.grivyzom.com](https://www.grivyzom.com)
- **Discord:** [Servidor de Discord](https://discord.gg/grivyzom)
- **Issues:** [GitHub Issues](https://github.com/Brocolitx/MarryCore/issues)

---

⭐ ¡No olvides dar una estrella al proyecto si te resultó útil!
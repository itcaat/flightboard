package com.flightboard;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FlightBoardPlugin extends JavaPlugin implements CommandExecutor {
    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();
    private static final String HOLOGRAM_NAME = "FlightBoard";
    private FileConfiguration config;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        config = getConfig(); // Загружаем конфиг

        if (getCommand("reloadboard") != null) {
            getCommand("reloadboard").setExecutor(this);
        }

        // Проверяем, загружены ли все параметры
        if (!config.contains("api-url") || !config.contains("hologram.world") ||
            !config.contains("hologram.x") || !config.contains("hologram.y") ||
            !config.contains("hologram.z") || !config.contains("hologram.update-interval")) {
            getLogger().warning("[FlightBoard] Конфиг не загружен! Проверьте config.yml");
            return;
        }

        getLogger().info("[FlightBoard] Плагин запущен!");
        getLogger().info("API URL: " + config.getString("api-url"));

        long updateIntervalTicks = getConfig().getInt("hologram.update-interval") * 20L;
        getLogger().info("[FlightBoard] Голограмма будет обновляться каждые " + (updateIntervalTicks / 20) + " секунд.");

        updateHologram();

        // Запускаем таймер обновления
        new BukkitRunnable() {
            @Override
            public void run() {
                updateHologram();
            }
        }.runTaskTimer(this, 600L, updateIntervalTicks);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("reloadboard")) {
            if (!sender.hasPermission("flightboard.reload")) {
                sender.sendMessage(ChatColor.RED + "У вас нет прав на выполнение этой команды!");
                return true;
            }

            sender.sendMessage(ChatColor.GREEN + "Обновление голограммы...");
            updateHologram();
            sender.sendMessage(ChatColor.GREEN + "Голограмма обновлена!");
            return true;
        }
        return false;
    }

    private void updateHologram() {
        String baseUrl = config.getString("api-url");
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                List<String> flightInfoList = new ArrayList<>();

                // Добавляем вылеты
                JsonArray departures = fetchFlights(baseUrl + "?type=departure");
                if (departures != null) {
                    flightInfoList.add(ChatColor.YELLOW + "** Вылеты **");
                    flightInfoList.addAll(parseFlights(departures, true)); // true = вылеты
                }

                // Добавляем прилёты
                JsonArray arrivals = fetchFlights(baseUrl + "?type=arrival");
                if (arrivals != null) {
                    flightInfoList.add(""); // Разделитель между секциями
                    flightInfoList.add(ChatColor.AQUA + "** Прилёты **");
                    flightInfoList.addAll(parseFlights(arrivals, false)); // false = прилёты
                }

                Bukkit.getScheduler().runTask(this, () -> createOrUpdateHologram(flightInfoList));
            } catch (Exception e) {
                e.printStackTrace();
                getLogger().warning("[FlightBoard] Ошибка при получении данных из API!");
            }
        });
    }

    private JsonArray fetchFlights(String apiUrl) {
        try {
            Request request = new Request.Builder().url(apiUrl).build();
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                getLogger().warning("[FlightBoard] Ошибка запроса к API: " + response.code());
                return null;
            }

            String json = response.body().string();
            return gson.fromJson(json, JsonArray.class);
        } catch (IOException e) {
            e.printStackTrace();
            getLogger().warning("[FlightBoard] Ошибка при получении данных из API!");
            return null;
        }
    }

    private List<String> parseFlights(JsonArray flights, boolean isDeparture) {
        List<String> flightInfoList = new ArrayList<>();

        for (int i = 0; i < Math.min(flights.size(), 5); i++) {
            JsonObject flight = flights.get(i).getAsJsonObject();

            String flightNumber = flight.get(isDeparture ? "OD_FLIGHT_NUMBER" : "OA_FLIGHT_NUMBER").getAsString();
            String destination = flight.get(isDeparture ? "OD_RAP_DESTINATION_NAME_RU" : "OA_RAP_ORIGIN_NAME_RU").getAsString();
            String airline = flight.get(isDeparture ? "OD_RAL_NAME_RUS" : "OA_RAL_NAME_RUS").getAsString();
            String aircraftType = flight.get(isDeparture ? "OD_RACT_ICAO_CODE" : "OA_RACT_ICAO_CODE").getAsString();
            String time = flight.get(isDeparture ? "OD_STD" : "OA_STA").getAsString().split("T")[1].substring(0, 5);

            String status = "По расписанию";
            if (flight.has(isDeparture ? "OD_STATUS_RU" : "OA_STATUS_RU") && flight.get(isDeparture ? "OD_STATUS_RU" : "OA_STATUS_RU").isJsonPrimitive()) {
                status = flight.get(isDeparture ? "OD_STATUS_RU" : "OA_STATUS_RU").getAsString().trim();
            }

            ChatColor statusColor = status.equalsIgnoreCase("По расписанию") ? ChatColor.GREEN : ChatColor.RED;

            flightInfoList.add(ChatColor.YELLOW + "🕒 " + time + " | ✈ " + flightNumber + " | " + ChatColor.AQUA + destination);
            flightInfoList.add(ChatColor.GRAY + "🛫 " + airline + " | " + ChatColor.GOLD + aircraftType + " | " + statusColor + status);
        }

        return flightInfoList;
    }

    private void createOrUpdateHologram(List<String> flightInfoList) {
        World world = Bukkit.getWorld(config.getString("hologram.world"));
        if (world == null) {
            getLogger().warning("[FlightBoard] Мир '" + config.getString("hologram.world") + "' не найден!");
            return;
        }

        double x = config.getDouble("hologram.x");
        double y = config.getDouble("hologram.y");
        double z = config.getDouble("hologram.z");

        Location holoLocation = new Location(world, x, y, z);

        if (DHAPI.getHologram(HOLOGRAM_NAME) != null) {
            DHAPI.removeHologram(HOLOGRAM_NAME);
        }

        Hologram hologram = DHAPI.createHologram(HOLOGRAM_NAME, holoLocation);
        DHAPI.setHologramLines(hologram, flightInfoList);
    }
}

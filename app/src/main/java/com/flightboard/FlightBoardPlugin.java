package com.flightboard;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
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
            !config.contains("hologram.z")) {
            getLogger().warning("[FlightBoard] Конфиг не загружен! Проверьте config.yml");
            return;
        }

        getLogger().info("[FlightBoard] Плагин запущен!");
        getLogger().info("API URL: " + config.getString("api-url"));
        getLogger().info("Координаты загружены: " + 
                config.getDouble("hologram.x") + ", " + 
                config.getDouble("hologram.y") + ", " + 
                config.getDouble("hologram.z"));

        long updateIntervalTicks = getConfig().getInt("hologram.update-interval") * 20L;
        getLogger().info("[FlightBoard] Голограмма будет обновляться каждые " + (updateIntervalTicks / 20) + " секунд.");

        updateHologram();

        // Запускаем таймер для обновления раз в 5 минут
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
        String apiUrl = config.getString("api-url");
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                Request request = new Request.Builder().url(apiUrl).build();
                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    getLogger().warning("[FlightBoard] Ошибка запроса к API: " + response.code());
                    return;
                }

                String json = response.body().string();
                JsonArray flights = gson.fromJson(json, JsonArray.class);
                List<String> flightInfoList = parseFlights(flights);

                Bukkit.getScheduler().runTask(this, () -> createOrUpdateHologram(flightInfoList));
            } catch (IOException e) {
                e.printStackTrace();
                getLogger().warning("[FlightBoard] Ошибка при получении данных из API!");
            }
        });
    }

    private List<String> parseFlights(JsonArray flights) {
        List<String> flightInfoList = new ArrayList<>();
        flightInfoList.add(ChatColor.BLUE + "***** Аэропорт Пулково (Санкт-Петербург) *****");
        flightInfoList.add(ChatColor.YELLOW + "** Табло вылета **");

        for (int i = 0; i < Math.min(flights.size(), 6); i++) {
            JsonObject flight = flights.get(i).getAsJsonObject();

            // Извлекаем данные из API
            String flightNumber = flight.get("OD_FLIGHT_NUMBER").getAsString();
            String destination = flight.get("OD_RAP_DESTINATION_NAME_RU").getAsString();
            String airline = flight.get("OD_RAL_NAME_RUS").getAsString();
            String aircraftType = flight.get("OD_RACT_ICAO_CODE").getAsString();
            String departureTime = flight.get("OD_STD").getAsString().split("T")[1].substring(0, 5); // HH:mm

            // Проверка статуса
            String status = "По расписанию"; // По умолчанию
            if (flight.has("OD_STATUS_RU") && flight.get("OD_STATUS_RU").isJsonPrimitive()) {
                status = flight.get("OD_STATUS_RU").getAsString().trim();
            }

            // Цветовое оформление статуса
            ChatColor statusColor = status.equalsIgnoreCase("По расписанию") || status.equalsIgnoreCase("On Time")
                    ? ChatColor.GREEN // ✅ Зелёный
                    : ChatColor.RED;   // ❌ Красный

            // Формируем строки для голограммы
            String line1 = ChatColor.YELLOW + "🕒 " + departureTime + " | ✈ " + flightNumber + " | " + ChatColor.AQUA
                    + destination;
            String line2 = ChatColor.GRAY + "🛫 " + airline + " | " + ChatColor.GOLD + aircraftType + " | "
                    + statusColor + status;

            flightInfoList.add(line1 + line2);
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

        // Удаляем старую голограмму
        if (DHAPI.getHologram(HOLOGRAM_NAME) != null) {
            DHAPI.removeHologram(HOLOGRAM_NAME);
        }

        // Создаём новую голограмму
        Hologram hologram = DHAPI.createHologram(HOLOGRAM_NAME, holoLocation);
        DHAPI.setHologramLines(hologram, flightInfoList);
    }
}

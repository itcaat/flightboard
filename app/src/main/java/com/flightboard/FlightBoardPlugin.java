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
        config = getConfig();

        if (getCommand("reloadboard") != null) {
            getCommand("reloadboard").setExecutor(this);
        }

        if (!config.contains("api-url") || !config.contains("hologram.world") ||
                !config.contains("hologram.x") || !config.contains("hologram.y") ||
                !config.contains("hologram.z") || !config.contains("hologram.update-interval")) {
            getLogger().warning("[FlightBoard] Конфиг не загружен! Проверьте config.yml");
            return;
        }

        getLogger().info("[FlightBoard] Плагин запущен!");
        getLogger().info("API URL: " + config.getString("api-url"));

        long updateIntervalTicks = getConfig().getInt("hologram.update-interval") * 20L;
        getLogger().info("[FlightBoard] Голограмма обновляется каждые " + (updateIntervalTicks / 20) + " секунд.");

        updateHologram();

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

                // Получаем вылеты
                JsonArray departures = fetchFlights(baseUrl + "?type=departure");
                if (departures != null) {
                    flightInfoList.add(ChatColor.YELLOW + "** Вылеты **");
                    flightInfoList.addAll(parseDepartures(departures));
                }

                // Разделитель между секциями
                flightInfoList.add("");

                // Получаем прилёты
                JsonArray arrivals = fetchFlights(baseUrl + "?type=arrival");
                if (arrivals != null) {
                    flightInfoList.add(ChatColor.AQUA + "** Прилёты **");
                    flightInfoList.addAll(parseArrivals(arrivals));
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

    // === Вылеты ===

    private List<String> parseDepartures(JsonArray flights) {
        List<String> flightInfoList = new ArrayList<>();

        for (int i = 0; i < flights.size(); i++) {
            JsonObject flight = flights.get(i).getAsJsonObject();

            String flightNumber = flight.get("OD_FLIGHT_NUMBER").getAsString();
            String destination = flight.get("OD_RAP_DESTINATION_NAME_RU").getAsString();
            String airline = flight.get("OD_RAL_NAME_RUS").getAsString();
            String aircraftType = flight.get("OD_RACT_ICAO_CODE").getAsString();
            String time = flight.get("OD_STD").getAsString().split("T")[1].substring(0, 5); // HH:mm

            String status = "?";
            ChatColor statusColor = ChatColor.GREEN;

            // Если есть OD_OFFBLOCK, значит рейс отправлен
            if (flight.has("OD_OFFBLOCK") && flight.get("OD_OFFBLOCK").isJsonPrimitive()) {
                String offblockTime = flight.get("OD_OFFBLOCK").getAsString().split("T")[1].substring(0, 5);
                status = "Отправлен в " + offblockTime + " (MSK)";
                statusColor = ChatColor.GRAY;
            } else if (flight.has("OD_BOARDING_END_ACTUAL") && flight.get("OD_BOARDING_END_ACTUAL").isJsonPrimitive()) {
                String onboardEndTime = flight.get("OD_BOARDING_END_ACTUAL").getAsString().split("T")[1].substring(0,
                        5);
                status = "Посадка окончена в " + onboardEndTime + " (MSK)";
                statusColor = ChatColor.GRAY;
            } else if (flight.has("OD_BOARDING_BOARDING_SECOND") && flight.get("OD_BOARDING_BOARDING_SECOND").isJsonPrimitive()) {
                String onboardEndTime = flight.get("OD_BOARDING_BOARDING_SECOND").getAsString().split("T")[1].substring(0,
                        5);
                status = "Посадка окончена в " + onboardEndTime + " (MSK)";
                statusColor = ChatColor.GRAY;
            } else if (flight.has("OD_BOARDING_BOARDING_ACTUAL")
                    && flight.get("OD_BOARDING_BOARDING_ACTUAL").isJsonPrimitive()) {
                // если есть OD_BOARDING_BOARDING_ACTUAL то и идет посадка
                status = "Идет посадка";
                statusColor = ChatColor.GREEN;
            } else if (flight.has("OD_COUNTER_END_ACTUAL") && flight.get("OD_COUNTER_END_ACTUAL").isJsonPrimitive()) {
                // если есть то идет посадка - по факту это костыль. Приходится на него
                // ориентироваться так как не всегда есть OD_BOARDING_BOARDING_ACTUAL
                status = "Идет посадка";
                statusColor = ChatColor.GREEN;
            } else if (flight.has("OD_COUNTER_BEGIN_ACTUAL") && flight.get("OD_COUNTER_BEGIN_ACTUAL").isJsonPrimitive()
                    && flight.get("OD_COUNTERS").isJsonPrimitive()) {
                // если есть то идет посадка - по факту это костыль. Приходится на него
                // ориентироваться так как не всегда есть OD_BOARDING_BOARDING_ACTUAL
                status = "Регистрация на " + flight.get("OD_COUNTERS").getAsString();
                statusColor = ChatColor.GRAY;
            }

            else if (flight.has("OD_STATUS_RU") && flight.get("OD_STATUS_RU").isJsonPrimitive()) {
                try {
                    status = ChatColor.RED + flight.get("OD_STATUS_RU").getAsString().trim();
                } catch (UnsupportedOperationException e) {
                    status = ChatColor.GREEN + "По расписанию"; // Значение по умолчанию
                }
            }

            flightInfoList.add(ChatColor.YELLOW + "🕒 " + time + " | ✈ " + flightNumber + " | " + ChatColor.AQUA
                    + destination + ChatColor.GRAY + "🛫 " + airline + " | " + ChatColor.GOLD + aircraftType + " | "
                    + statusColor + status);
        }

        return flightInfoList;
    }

    // === Прилёты ===
    private List<String> parseArrivals(JsonArray flights) {
        List<String> flightInfoList = new ArrayList<>();

        for (int i = 0; i < flights.size(); i++) {
            JsonObject flight = flights.get(i).getAsJsonObject();

            String flightNumber = flight.get("OA_FLIGHT_NUMBER").getAsString();
            String origin = flight.get("OA_RAP_ORIGIN_NAME_RU").getAsString();
            String airline = flight.get("OA_RAL_NAME_RUS").getAsString();
            String aircraftType = flight.get("OA_RACT_ICAO_CODE").getAsString();
            String time = flight.get("OA_STA").getAsString().split("T")[1].substring(0, 5);

            String status = "По расписанию";
            if (flight.has("OA_STATUS_RU") && flight.get("OA_STATUS_RU").isJsonPrimitive()) {
                status = flight.get("OA_STATUS_RU").getAsString().trim();
            }

            ChatColor statusColor = status.equalsIgnoreCase("По расписанию") ? ChatColor.GREEN : ChatColor.RED;

            flightInfoList.add(ChatColor.YELLOW + "🕒 " + time + " | ✈ " + flightNumber + " | " + ChatColor.AQUA
                    + origin + ChatColor.GRAY + "🛬 " + airline + " | " + ChatColor.GOLD + aircraftType + " | "
                    + statusColor + status);
        }

        return flightInfoList;
    }

    private void createOrUpdateHologram(List<String> flightInfoList) {
        World world = Bukkit.getWorld(config.getString("hologram.world"));
        if (world == null) {
            getLogger().warning("[FlightBoard] Мир '" + config.getString("hologram.world") + "' не найден!");
            return;
        }

        Location holoLocation = new Location(world, config.getDouble("hologram.x"), config.getDouble("hologram.y"),
                config.getDouble("hologram.z"));

        if (DHAPI.getHologram(HOLOGRAM_NAME) != null) {
            DHAPI.removeHologram(HOLOGRAM_NAME);
        }

        Hologram hologram = DHAPI.createHologram(HOLOGRAM_NAME, holoLocation);
        DHAPI.setHologramLines(hologram, flightInfoList);
    }
}

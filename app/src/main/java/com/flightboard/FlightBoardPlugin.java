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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FlightBoardPlugin extends JavaPlugin implements CommandExecutor {
    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();
    private static final String HOLOGRAM_NAME = "FlightBoard";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getCommand("reloadboard").setExecutor(this);
        Bukkit.getLogger().info("[FlightBoard] Плагин запущен!");

        // Первое обновление при старте
        updateHologram();

        // Запускаем таймер для обновления каждые 5 минут
        new BukkitRunnable() {
            @Override
            public void run() {
                updateHologram();
            }
        }.runTaskTimer(this, 600L, 6000L);
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
        String apiUrl = getConfig().getString("api-url");
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                Request request = new Request.Builder().url(apiUrl).build();
                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    Bukkit.getLogger().warning("[FlightBoard] Ошибка запроса к API");
                    return;
                }

                String json = response.body().string();
                JsonArray flights = gson.fromJson(json, JsonArray.class);
                List<String> flightInfoList = parseFlights(flights);

                Bukkit.getScheduler().runTask(this, () -> createOrUpdateHologram(flightInfoList));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private List<String> parseFlights(JsonArray flights) {
        List<String> flightInfoList = new ArrayList<>();
        flightInfoList.add(ChatColor.BLUE + "**Актуальные рейсы**");

        for (int i = 0; i < Math.min(flights.size(), 6); i++) { // Показываем до 6 рейсов
            JsonObject flight = flights.get(i).getAsJsonObject();

            // Извлекаем данные из API
            String flightNumber = flight.get("OD_FLIGHT_NUMBER").getAsString();
            String destination = flight.get("OD_RAP_DESTINATION_NAME_RU").getAsString();
            String airline = flight.get("OD_RAL_NAME_RUS").getAsString();
            String aircraftType = flight.get("OD_RACT_ICAO_CODE").getAsString();
            String departureTime = flight.get("OD_STD").getAsString().split("T")[1].substring(0, 5); // HH:mm
            String status = ""; // Значение по умолчанию

            try {
                status = ChatColor. RED + flight.get("OD_STATUS_RU").getAsString().trim();
            } catch (UnsupportedOperationException e) {
                status = ChatColor.GREEN + "✅ По расписанию"; // Значение по умолчанию
            }

            // Форматируем строки для голограммы
            String line1 = ChatColor.YELLOW + "🕒 " + departureTime + " | ✈ " + flightNumber + " | " + ChatColor.AQUA
                    + destination;
            String line2 = ChatColor.GRAY + "🛫 " + airline + " | " + ChatColor.GOLD + aircraftType + " | "
                    + status;

            flightInfoList.add(line1);
            flightInfoList.add(line2);
        }

        return flightInfoList;
    }

    private void createOrUpdateHologram(List<String> flightInfoList) {
        Location holoLocation = new Location(Bukkit.getWorld(getConfig().getString("hologram.world")),
                getConfig().getDouble("hologram.x"),
                getConfig().getDouble("hologram.y"),
                getConfig().getDouble("hologram.z"));

        // Удаляем старую голограмму, если есть
        if (DHAPI.getHologram(HOLOGRAM_NAME) != null) {
            DHAPI.removeHologram(HOLOGRAM_NAME);
        }

        // Создаём новую голограмму
        Hologram hologram = DHAPI.createHologram(HOLOGRAM_NAME, holoLocation);
        DHAPI.setHologramLines(hologram, flightInfoList);
    }
}

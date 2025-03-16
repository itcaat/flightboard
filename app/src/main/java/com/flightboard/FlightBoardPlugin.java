package com.flightboard;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getCommand("reloadboard").setExecutor(this);
        Bukkit.getLogger().info("[FlightBoard] Плагин запущен!");

        // Первое обновление при старте
        updateBillboards();

        // Запускаем таймер для автоматического обновления
        new BukkitRunnable() {
            @Override
            public void run() {
                updateBillboards();
            }
        }.runTaskTimer(this, 600L, 6000L); // Обновление раз в 5 минут
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("reloadboard")) {
            if (!sender.hasPermission("flightboard.reload")) {
                sender.sendMessage(ChatColor.RED + "У вас нет прав на выполнение этой команды!");
                return true;
            }

            sender.sendMessage(ChatColor.GREEN + "Обновление билбордов...");
            updateBillboards();
            sender.sendMessage(ChatColor.GREEN + "Билборды обновлены!");
            return true;
        }
        return false;
    }

    private void updateBillboards() {
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

                Bukkit.getScheduler().runTask(this, () -> updateSigns(flightInfoList));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private List<String> parseFlights(JsonArray flights) {
        List<String> flightInfoList = new ArrayList<>();
        for (int i = 0; i < Math.min(flights.size(), 3); i++) {
            JsonObject flight = flights.get(i).getAsJsonObject();
            String flightNumber = flight.get("OD_FLIGHT_NUMBER").getAsString();
            String destination = flight.get("OD_RAP_DESTINATION_NAME_RU").getAsString();
            String departureTime = flight.get("OD_STD").getAsString().split("T")[1].substring(0, 5); // HH:mm

            String flightInfo = ChatColor.YELLOW + flightNumber + " -> " + ChatColor.AQUA + destination + " " + ChatColor.GRAY + departureTime;
            flightInfoList.add(flightInfo);
        }
        return flightInfoList;
    }

    private void updateSigns(List<String> flightInfoList) {
        List<Sign> signs = findFlightBoardSigns();
    
        if (signs.isEmpty()) {
            Bukkit.getLogger().warning("[FlightBoard] Не найдено табличек с [FlightBoard]");
            return;
        }
    
        for (Sign sign : signs) {
            // Проверяем, что первая строка уже содержит [FlightBoard]
            if (!sign.getLine(0).equalsIgnoreCase("[FlightBoard]")) {
                continue; // Если нет, пропускаем эту табличку
            }
    
            // Обновляем только строки 1-3 (с рейсами)
            for (int i = 0; i < 3; i++) {
                if (i < flightInfoList.size()) {
                    sign.setLine(i + 1, flightInfoList.get(i)); // Заполняем строку рейсом
                } else {
                    sign.setLine(i + 1, ""); // Если рейсов меньше 3-х, очищаем строку
                }
            }
    
            sign.update(); // Применяем изменения
        }
    }
    

    private List<Sign> findFlightBoardSigns() {
        List<Sign> signs = new ArrayList<>();
        
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (state instanceof Sign sign) {  // Проверяем, является ли блок табличкой
                        if (sign.getLine(0).equalsIgnoreCase("[FlightBoard]")) {
                            signs.add(sign);
                        }
                    }
                }
            }
        }
        
        return signs;
    }
}

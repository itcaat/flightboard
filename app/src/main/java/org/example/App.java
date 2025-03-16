package org.example;

import com.google.gson.*;
import okhttp3.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;


public class App extends JavaPlugin {

    private final OkHttpClient client = new OkHttpClient();

    @Override
    public void onEnable() {
        getLogger().info("MegoSearchPlugin включен!");
        this.getCommand("megosearch").setExecutor(new SearchCommand());
    }

    class SearchCommand implements CommandExecutor {

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

            if (args.length < 3) {
                sender.sendMessage("Использование: //megosearch <from> <where> <flightdate> [flightdateback]");
                return false;
            }

            String from = args[0];
            String where = args[1];
            String flightdate = args[2];

            JsonArray sections = new JsonArray();

            // Добавляем перелёт "туда"
            JsonObject sectionTo = new JsonObject();
            sectionTo.addProperty("from", from);
            sectionTo.addProperty("to", where);
            sectionTo.addProperty("date", flightdate + "T00:00:00");
            sections.add(sectionTo);

            String flightType = "туда";

            // Если указана обратная дата, добавляем и обратный перелёт
            if (args.length >= 4) {
                String flightdateback = args[3];
                JsonObject sectionBack = new JsonObject();
                sectionBack.addProperty("from", where);
                sectionBack.addProperty("to", from);
                sectionBack.addProperty("date", flightdateback + "T00:00:00");
                sections.add(sectionBack);
                flightType = "туда-обратно";
            }

            // Формируем JSON-запрос
            JsonObject requestJson = new JsonObject();
            requestJson.add("sections", sections);
            requestJson.addProperty("adults", 1);
            requestJson.addProperty("children", 0);
            requestJson.addProperty("babies", 0);
            requestJson.addProperty("bookingClass", "Economic");
            requestJson.addProperty("isRangeSearch", false);
            requestJson.addProperty("partnerCookie", "");
            requestJson.add("bookingSessionId", JsonNull.INSTANCE);
            requestJson.addProperty("marketingChannelStatus", "");
            requestJson.addProperty("appId", "C5E23A9A58F2C1E9CA7B0F594813719C0458EF08");
            requestJson.addProperty("appSecret", "AD73681BC741FDEDF388EF7332546DCC17001C5500CC2E99608B76BA9BD626D2");
            requestJson.addProperty("currencyCode", "RUB");
            requestJson.addProperty("locale", "ru");
            requestJson.addProperty("theme", "mego");
            requestJson.addProperty("host", "mego.travel");
            requestJson.addProperty("instance", "frontend4");
            requestJson.addProperty("marketCode", "ru");

            Request request = new Request.Builder()
                    .url("https://f-api.mego.travel/api/Flight/Search")
                    .post(RequestBody.create(requestJson.toString(), MediaType.parse("application/json")))
                    .addHeader("Accept", "application/json")
                    .build();

            String finalFlightType = flightType;
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    getLogger().severe("Ошибка запроса: " + e.getMessage());
                    sender.sendMessage("Ошибка поиска рейсов.");
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

                        JsonArray flights = root.getAsJsonArray("flights");
                        if (flights.size() == 0) {
                            sender.sendMessage("Рейсы не найдены.");
                            return;
                        }

                        // Ищем самый дешевый рейс
                        JsonObject cheapestFlight = null;
                        double minPrice = Double.MAX_VALUE;

                        for (JsonElement flightElem : flights) {
                            JsonObject flight = flightElem.getAsJsonObject();
                            double billingPrice = flight.get("billingPrice").getAsDouble();
                            if (billingPrice < minPrice) {
                                minPrice = billingPrice;
                                cheapestFlight = flight;
                            }
                        }

                        if (cheapestFlight != null) {
                            JsonArray legs = cheapestFlight.getAsJsonArray("legs");
                            StringBuilder flightDescription = new StringBuilder();

                            for (JsonElement legElem : legs) {
                                JsonObject leg = legElem.getAsJsonObject();
                                JsonObject segment = leg.getAsJsonArray("segments").get(0).getAsJsonObject();
                                String departureDateLocal = segment.get("departureDateLocal").getAsString().substring(0,
                                        10);
                                String flightCode = segment.get("flightCode").getAsString();
                                String departureTerminal = segment.get("departureTerminal").getAsString();
                                String arrivalTerminal = segment.get("arrivalTerminal").getAsString();
                                String fromCode = segment.get("departureDestinationUID").getAsString();
                                String toCode = segment.get("arrivalDestinationUID").getAsString();

                                flightDescription.append(String.format(
                                        "%s: %s → %s (%s, %s → %s)%n",
                                        departureDateLocal,
                                        fromCode,
                                        toCode,
                                        flightCode,
                                        departureTerminal,
                                        arrivalTerminal));
                            }

                            String result = String.format(
                                    "Самый дешевый рейс (%s):\n%sЦена: %.2f RUB",
                                    legs.size() > 1 ? "туда-обратно" : "туда",
                                    flightDescription,
                                    minPrice);

                            getLogger().info(result);
                            if (sender instanceof Player) {
                                sender.sendMessage(result);
                            }
                        } else {
                            sender.sendMessage("Рейсы не найдены.");
                        }
                    } else {
                        sender.sendMessage("Ошибка при поиске рейсов.");
                    }
                }

            });

            sender.sendMessage("Выполняется поиск рейсов (" + flightType + ")...");
            return true;
        }
    }
}

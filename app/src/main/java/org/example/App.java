package org.example;

import com.google.gson.*;
import okhttp3.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class App extends JavaPlugin {

    private final OkHttpClient client = new OkHttpClient();

    @Override
    public void onEnable() {
        getLogger().info("MegoSearchPlugin включен!");
        this.getCommand("megosearch").setExecutor(new SearchCommand());
    }

    class SearchCommand implements CommandExecutor {

        private final DateTimeFormatter urlDateFormatter = DateTimeFormatter.ofPattern("MMdd");

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

            JsonObject sectionTo = new JsonObject();
            sectionTo.addProperty("from", from);
            sectionTo.addProperty("to", where);
            sectionTo.addProperty("date", flightdate + "T00:00:00");
            sections.add(sectionTo);

            boolean isRoundTrip = args.length >= 4;

            if (isRoundTrip) {
                String flightdateback = args[3];
                JsonObject sectionBack = new JsonObject();
                sectionBack.addProperty("from", where);
                sectionBack.addProperty("to", from);
                sectionBack.addProperty("date", flightdateback + "T00:00:00");
                sections.add(sectionBack);
            }

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
                        String searchId = root.get("searchId").getAsString();

                        JsonArray flights = root.getAsJsonArray("flights");
                        if (flights.size() == 0) {
                            sender.sendMessage("Рейсы не найдены.");
                            return;
                        }

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
                            StringBuilder flightIds = new StringBuilder();

                            for (JsonElement legElem : legs) {
                                JsonObject segment = legElem.getAsJsonObject()
                                    .getAsJsonArray("segments").get(0).getAsJsonObject();

                                if (flightIds.length() > 0) flightIds.append("_");
                                flightIds.append(segment.get("flightId").getAsString());
                            }

                            LocalDate dateTo = LocalDate.parse(flightdate);
                            String url = String.format("https://mego.travel/flights/%s/%s/%s/100/e/booking/%s/%s",
                                from, where, dateTo.format(urlDateFormatter), searchId, flightIds);

                            if (isRoundTrip) {
                                LocalDate dateBack = LocalDate.parse(args[3]);
                                url = String.format("https://mego.travel/flights/%s/%s/%s/%s/%s/%s/100/e/booking/%s/%s",
                                    from, where, dateTo.format(urlDateFormatter),
                                    where, from, dateBack.format(urlDateFormatter), searchId, flightIds);
                            }

                            String result = String.format("Самый дешевый рейс: %.2f RUB\n%s", minPrice, url);
                            sender.sendMessage(result);
                        } else {
                            sender.sendMessage("Рейсы не найдены.");
                        }
                    } else {
                        sender.sendMessage("Ошибка при поиске рейсов.");
                    }
                }
            });

            sender.sendMessage("Выполняется поиск рейсов...");
            return true;
        }
    }
}

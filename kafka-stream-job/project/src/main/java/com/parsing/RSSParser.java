package com.parsing;

import java.util.regex.*;
import java.util.ArrayList;
import org.json.JSONObject;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public class RSSParser {
    private String toParse;
    private String city;
    private String state;
    private ArrayList<String> category;
    private ArrayList<Integer> aqi;
    private ArrayList<String> pollutant;
    private String lastUpdate;

    public RSSParser(String rssContent) {
        this.toParse = rssContent;
        this.city = "";
        this.state = "";
        this.category = new ArrayList<String>();
        this.aqi = new ArrayList<Integer>();
        this.pollutant = new ArrayList<String>();
        this.lastUpdate = "";

        parseLocation();
        parsePollutant();
        parseLastUpdate();
    }

    //On récupère le nom de la ville et le code de l'état
    private void parseLocation() {
        Pattern cityState = Pattern.compile("<b>Location:</b>\\s*([A-Za-zÀ-ÿ\\s\\-,().']+),\\s*([A-Z]{2})");
        Matcher matcher = cityState.matcher(this.toParse);

        if (matcher.find()) {
            this.city = matcher.group(1).trim();
            this.state = matcher.group(2).trim();
        }
    }

    //On récupère la catégorie et l'AQI des polluants
    private void parsePollutant() {
        Pattern aqiPattern = Pattern.compile("([A-Za-z]+)\\s*-\\s*(\\d+) AQI - ([^<\\n]+)");
        Matcher matcher = aqiPattern.matcher(this.toParse);

        while (matcher.find()) {
            this.category.add(matcher.group(1).trim());
            this.aqi.add(Integer.parseInt(matcher.group(2).trim()));

            Pattern pollutantPattern = Pattern.compile("^Particle Pollution \\(((\\d+)(\\.\\d+)?) microns\\)$");
            Matcher pollutantMatcher = pollutantPattern.matcher(matcher.group(3).trim());
            if (pollutantMatcher.find()) {
                this.pollutant.add("PM"+pollutantMatcher.group(1).trim());
            } else {
                this.pollutant.add("O3");
            }
        }
    }

    //On récupère la date de la dernière mise à jour
    private void parseLastUpdate() {
        Pattern updatePat = Pattern.compile("Last Update: ([^<]+)");
        Matcher matcher = updatePat.matcher(this.toParse);

        if (!matcher.find()) {
            System.err.println("Aucun 'Last Update' trouvé dans: " + this.toParse);
            return;
        }

        String dateStr = matcher.group(1).trim();

        // Nettoyage : enlève jour de semaine + virgule optionnelle
        String cleanDateStr = dateStr.replaceFirst("^(Mon|Tue|Wed|Thu|Fri|Sat|Sun),?\\s*", "").trim();

        // Formatter principal (24h + timezone)
        DateTimeFormatter formatter24h = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
            .withResolverStyle(ResolverStyle.LENIENT);

        // Formatter alternatif : si AM/PM présent
        DateTimeFormatter formatter12h = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("dd MMM yyyy hh:mm:ss a z")
            .toFormatter(Locale.ENGLISH)
            .withResolverStyle(ResolverStyle.LENIENT);

        try {
            ZonedDateTime zdt = ZonedDateTime.parse(cleanDateStr, formatter24h);
            this.lastUpdate = zdt.toInstant().toString();
            return;
        } catch (DateTimeParseException e1) {
            try {
                // Fallback 12h + AM/PM
                ZonedDateTime zdt = ZonedDateTime.parse(cleanDateStr, formatter12h);
                this.lastUpdate = zdt.toInstant().toString();
                return;
            } catch (DateTimeParseException e2) {
                System.err.println("Échec parsing date RSS: '" + dateStr + "'");
                System.err.println("Nettoyée: '" + cleanDateStr + "'");
                System.err.println("Erreur 24h: " + e1.getMessage());
                System.err.println("Erreur 12h:  " + e2.getMessage());
            }
        }
    }

    //On construit le JSON avec les données extraites
    public JSONObject getExtractedData() {
        JSONObject data = new JSONObject();
        data.put("city", this.city);
        data.put("state", this.state);
        data.put("lastUpdate", this.lastUpdate);

        ArrayList<JSONObject> pollutantsData = new ArrayList<>();
        for (int i = 0; i < this.category.size(); i++) {
            JSONObject pollutantData = new JSONObject();
            pollutantData.put("category", this.category.get(i));
            pollutantData.put("aqi", this.aqi.get(i));
            pollutantData.put("name", this.pollutant.get(i));
            pollutantsData.add(pollutantData);
        }
        data.put("pollutants", pollutantsData);

        return data;
    }
}
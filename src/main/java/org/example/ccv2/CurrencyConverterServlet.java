package org.example.ccv2;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonNumber;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

@WebServlet(name = "CurrencyConverterServlet", urlPatterns = {"/currency-converter"})
public class CurrencyConverterServlet extends HttpServlet {

    private static final String API_BASE_URL = "https://cdn.jsdelivr.net/gh/fawazahmed0/currency-api@1/latest/currencies";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String fromCurrency = request.getParameter("from");
        String toCurrency = request.getParameter("to");
        String amountParam = request.getParameter("amount");

        if (amountParam == null || amountParam.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "The 'amount' parameter is missing or empty.");
            return;
        }

        try {
            double amount = Double.parseDouble(amountParam.trim());
            double rate = fromCurrency.equals("usd") ? getExchangeRate(fromCurrency, toCurrency) : getInverseExchangeRate(fromCurrency, toCurrency);
            if (rate == 0.0) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to get exchange rate");
                return;
            }

            double convertedAmount = amount * rate;
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(String.format("{\"convertedAmount\": %.2f}", convertedAmount));
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid amount format");
        }
    }

    private double getExchangeRate(String fromCurrency, String toCurrency) throws IOException {
        String apiUrlString = API_BASE_URL + "/" + fromCurrency + ".json";
        URL apiUrl = new URL(apiUrlString);
        HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
        connection.setRequestMethod("GET");
        connection.connect();

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("API request failed with response code " + responseCode);
        }

        try (InputStream inputStream = connection.getInputStream();
             JsonReader jsonReader = Json.createReader(inputStream)) {
            JsonObject jsonObject = jsonReader.readObject();
            JsonObject rates = jsonObject.getJsonObject(fromCurrency.toLowerCase());
            if (rates == null) {
                throw new IOException("Could not find exchange rates for " + fromCurrency);
            }
            JsonNumber rate = rates.getJsonNumber(toCurrency.toLowerCase());
            if (rate == null) {
                throw new IOException("Could not find exchange rate for " + fromCurrency + " to " + toCurrency);
            }
            return rate.doubleValue();
        } finally {
            connection.disconnect();
        }
    }

    private double getInverseExchangeRate(String fromCurrency, String toCurrency) throws IOException {
        double directRate = getExchangeRate(toCurrency, fromCurrency);
        return directRate != 0.0 ? 1.0 / directRate : 0.0;
    }
}

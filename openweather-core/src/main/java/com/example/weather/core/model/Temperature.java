package com.example.weather.core.model;

public record Temperature(double value, TemperatureUnit unit) {

  public Temperature toUnit(TemperatureUnit targetUnit) {
    if (this.unit == targetUnit) return this;
    return switch (targetUnit) {
      case CELSIUS -> new Temperature(toCelsiusValue(), TemperatureUnit.CELSIUS);
      case FAHRENHEIT -> new Temperature(toFahrenheitValue(), TemperatureUnit.FAHRENHEIT);
    };
  }

  private double toCelsiusValue() {
    return switch (unit) {
      case CELSIUS -> value;
      case FAHRENHEIT -> (value - 32.0) * 5.0 / 9.0;
    };
  }

  private double toFahrenheitValue() {
    return switch (unit) {
      case CELSIUS -> value * 9.0 / 5.0 + 32.0;
      case FAHRENHEIT -> value;
    };
  }
}

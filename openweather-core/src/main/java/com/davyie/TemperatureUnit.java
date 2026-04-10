package com.davyie;

public enum TemperatureUnit {
  CELSIUS(InternalUnit.METRIC), // Celsius
  FAHRENHEIT(InternalUnit.IMPERIAL), // FAHRENHEIT
  KELVIN(InternalUnit.STANDARD); // KELVIN

  private final InternalUnit internalUnit;

  TemperatureUnit(InternalUnit internalUnit) {
    this.internalUnit = internalUnit;
  }

  public InternalUnit toInternal() {
    return this.internalUnit;
  }
}

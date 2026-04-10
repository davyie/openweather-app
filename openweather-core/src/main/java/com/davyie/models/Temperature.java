package com.davyie.models;

import com.davyie.TemperatureUnit;
import java.time.LocalTime;

public record Temperature(LocalTime time, TemperatureUnit unit, double temp) {}

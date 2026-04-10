package com.davyie.models;

import java.time.LocalDate;

public record DayTemperature(LocalDate date, Temperature temperature, String locationName) {}

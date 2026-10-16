package me.kvd.properties.format.properties;

import me.kvd.properties.spi.ConfigFormat;
import me.kvd.properties.spi.ConfigFormatProvider;

public final class PropertiesFormatProvider implements ConfigFormatProvider {

  @Override
  public ConfigFormat format() {
    return PropertiesFormat.INSTANCE;
  }

  @Override
  public String extension() {
    return ".properties";
  }
}
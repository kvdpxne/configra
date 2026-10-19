package me.kvd.configra.format.properties;

import me.kvd.configra.spi.ConfigFormat;
import me.kvd.configra.spi.ConfigFormatProvider;

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
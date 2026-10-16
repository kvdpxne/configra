package me.kvd.properties.format.ini;

import me.kvd.properties.spi.ConfigFormat;
import me.kvd.properties.spi.ConfigFormatProvider;

public final class IniFormatProvider implements ConfigFormatProvider {

  @Override
  public ConfigFormat format() {
    return IniFormat.INSTANCE;
  }

  @Override
  public String extension() {
    return ".ini";
  }
}
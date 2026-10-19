package me.kvd.configra.format.ini;

import me.kvd.configra.spi.ConfigFormat;
import me.kvd.configra.spi.ConfigFormatProvider;

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
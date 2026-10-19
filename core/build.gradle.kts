dependencies {
  arrayOf("escape", "options", "spi", "exceptions", "extras:util", "extras:diff").forEach {
    api(project(":$it"))
  }

  // Test-only: format modules ship META-INF/services providers,
  // required by the ServiceLoader-based conveniences tested here.
  arrayOf("ini", "properties").forEach {
    testImplementation(project(":formats:$it"))
  }
}
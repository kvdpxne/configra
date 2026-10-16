dependencies {
  arrayOf("api", "options", "spi", "core").forEach {
    api(project(":$it"))
  }
}
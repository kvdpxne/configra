dependencies {
  arrayOf("escape", "options", "spi", "exceptions", "extras:util", "extras:diff").forEach {
    api(project(":$it"))
  }
}
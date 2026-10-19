dependencies {
  api(project(":api"))
  api(project(":core"))

  arrayOf("ini", "properties").forEach {
    testImplementation(project(":formats:$it"))
  }
}
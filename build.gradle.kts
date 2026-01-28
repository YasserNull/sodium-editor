plugins {
    id("com.android.application") version "8.10.0" apply false
    id("com.android.library") version "8.10.0" apply false
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)

    project.allprojects.forEach { project ->
        delete(project.layout.buildDirectory)
    }
}

import androidx.compose.ui.text.platform.FontLoader

fun main() {
    FontLoader::class.java.declaredMethods.forEach {
        println(it)
    }
}
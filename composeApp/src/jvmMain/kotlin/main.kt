package tim.projekt.bsw
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import tim.projekt.bsw.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "BSW Search",
        icon = painterResource("icon.ico")
    ) {
        App()
    }
}

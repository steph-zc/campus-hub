package br.com.uri.campushub.ui.checkin

import android.app.Activity
import android.widget.Toast
import br.com.uri.campushub.R
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

// lê um QR Code com a câmera e devolve o texto. A tela de check-in usa a interface,
// e os testes do aparelho trocam por uma leitura simulada (a câmera não roda no emulador)
fun interface QrScanner {
    // onCode recebe o texto lido; desistir da leitura (voltar) não chama nada
    fun scan(activity: Activity, onCode: (String) -> Unit, onFailure: (Exception) -> Unit)
}

// leitor do Google Play Services (play-services-code-scanner): a tela da câmera é do sistema,
// então o app não pede permissão de câmera e só recebe o texto do código.
// Aceita só QR Code, o formato que o CampusHub gera (QrCodeImage).
// O leitor é um módulo que o Play Services baixa à parte: o pedido do manifesto só vale em
// instalações pela Play Store, então antes de abrir a câmera o app confere com o ModuleInstall
// (Play Services) se ele já está no aparelho e, se não, pede o download e espera terminar
class GmsQrScanner : QrScanner {

    override fun scan(activity: Activity, onCode: (String) -> Unit, onFailure: (Exception) -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = GmsBarcodeScanning.getClient(activity, options)
        val installer = ModuleInstall.getClient(activity)

        installer.areModulesAvailable(scanner)
            .addOnSuccessListener { response ->
                if (response.areModulesAvailable()) {
                    startScan(scanner, onCode, onFailure)
                } else {
                    download(activity, scanner, onCode, onFailure)
                }
            }
            .addOnFailureListener { error -> onFailure(error) }
    }

    // pede o leitor ao Play Services e abre a câmera quando o download termina;
    // o aviso na tela explica a espera da primeira vez
    private fun download(
        activity: Activity,
        scanner: GmsBarcodeScanner,
        onCode: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val installer = ModuleInstall.getClient(activity)
        Toast.makeText(activity, R.string.scanner_downloading, Toast.LENGTH_LONG).show()

        // acompanha o download; ao terminar (ou falhar) deixa de ouvir
        val listener = object : InstallStatusListener {
            override fun onInstallStatusUpdated(update: ModuleInstallStatusUpdate) {
                when (update.installState) {
                    InstallState.STATE_COMPLETED -> {
                        installer.unregisterListener(this)
                        startScan(scanner, onCode, onFailure)
                    }
                    InstallState.STATE_FAILED, InstallState.STATE_CANCELED -> {
                        installer.unregisterListener(this)
                        onFailure(IllegalStateException(activity.getString(R.string.scanner_unavailable)))
                    }
                    else -> Unit
                }
            }
        }
        val request = ModuleInstallRequest.newBuilder()
            .addApi(scanner)
            .setListener(listener)
            .build()
        installer.installModules(request)
            .addOnSuccessListener { response ->
                // já estava instalado (baixado entre a conferência e o pedido): não haverá aviso de progresso
                if (response.areModulesAlreadyInstalled()) {
                    installer.unregisterListener(listener)
                    startScan(scanner, onCode, onFailure)
                }
            }
            .addOnFailureListener { error ->
                installer.unregisterListener(listener)
                // sem Play Store ou sem conta Google (por exemplo, emulador sem Play Store): explica o motivo
                onFailure(IllegalStateException(activity.getString(R.string.scanner_unavailable), error))
            }
    }

    private fun startScan(scanner: GmsBarcodeScanner, onCode: (String) -> Unit, onFailure: (Exception) -> Unit) {
        scanner.startScan()
            .addOnSuccessListener { barcode -> onCode(barcode.rawValue.orEmpty()) }
            .addOnFailureListener { error -> onFailure(error) }
    }
}

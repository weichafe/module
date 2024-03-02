package cl.vc.module.protocolbuff;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

public class NumberGenerator {

    private static DecimalFormatSymbols formatoMilesDecimal() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("es", "ES"));
        symbols.setDecimalSeparator('.');
        symbols.setGroupingSeparator(',');
        return symbols;
    }


    public static String formatDouble(Double px) {
        NumberFormat numberFormat = NumberFormat.getNumberInstance(new Locale("es", "ES"));
        return numberFormat.format(px);
    }

    public static DecimalFormat getFormatNumberMilDec() {
        DecimalFormat decFormat = new DecimalFormat("#,###,###,##0.00000");
        decFormat.setDecimalFormatSymbols(formatoMilesDecimal());
        return decFormat;
    }
}

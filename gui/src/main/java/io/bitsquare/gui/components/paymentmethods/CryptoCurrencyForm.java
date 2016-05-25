/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.components.paymentmethods;

import io.bitsquare.common.util.Tuple2;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.gui.util.validation.AltCoinAddressValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.payment.CryptoCurrencyAccount;
import io.bitsquare.payment.CryptoCurrencyAccountContractData;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.payment.PaymentAccountContractData;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Optional;

import static io.bitsquare.gui.util.FormBuilder.*;

public class CryptoCurrencyForm extends PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(CryptoCurrencyForm.class);

    private final CryptoCurrencyAccount cryptoCurrencyAccount;
    private final AltCoinAddressValidator altCoinAddressValidator;
    private InputTextField addressInputTextField;

    private ComboBox<TradeCurrency> currencyComboBox;
    private Label addressLabel;

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountContractData paymentAccountContractData, String labelTitle) {
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, labelTitle, ((CryptoCurrencyAccountContractData) paymentAccountContractData).getAddress());
        return gridRow;
    }

    public CryptoCurrencyForm(PaymentAccount paymentAccount, AltCoinAddressValidator altCoinAddressValidator, InputValidator inputValidator, GridPane gridPane,
                              int gridRow, BSFormatter formatter) {
        super(paymentAccount, inputValidator, gridPane, gridRow, formatter);
        this.cryptoCurrencyAccount = (CryptoCurrencyAccount) paymentAccount;
        this.altCoinAddressValidator = altCoinAddressValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        addTradeCurrencyComboBox();
        currencyComboBox.setPrefWidth(250);
        Tuple2<Label, InputTextField> tuple2 = addLabelInputTextField(gridPane, ++gridRow, "Cryptocurrency address:");
        addressLabel = tuple2.first;
        addressInputTextField = tuple2.second;
        addressInputTextField.setValidator(altCoinAddressValidator);

        addressInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            cryptoCurrencyAccount.setAddress(newValue);
            updateFromInputs();
        });

        addAllowedPeriod();
        addAccountNameTextFieldWithAutoFillCheckBox();
    }

    @Override
    public void updateFromInputs() {
        if (addressLabel != null && cryptoCurrencyAccount.getSingleTradeCurrency() != null)
            addressLabel.setText(cryptoCurrencyAccount.getSingleTradeCurrency().getName() + " address:");
        super.updateFromInputs();
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameCheckBox != null && !useCustomAccountNameCheckBox.isSelected()) {
            String method = BSResources.get(paymentAccount.getPaymentMethod().getId());
            String address = addressInputTextField.getText();
            address = StringUtils.abbreviate(address, 9);
            String currency = paymentAccount.getSingleTradeCurrency() != null ? paymentAccount.getSingleTradeCurrency().getCode() : "?";
            accountNameTextField.setText(method.concat(", ").concat(currency).concat(", ").concat(address));
        }
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addLabelTextField(gridPane, gridRow, "Account name:", cryptoCurrencyAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++gridRow, "Payment method:", BSResources.get(cryptoCurrencyAccount.getPaymentMethod().getId()));
        Tuple2<Label, TextField> tuple2 = addLabelTextField(gridPane, ++gridRow, "Cryptocurrency address:", cryptoCurrencyAccount.getAddress());
        addressLabel = tuple2.first;
        TextField field = tuple2.second;
        field.setMouseTransparent(false);
        addLabelTextField(gridPane, ++gridRow, "Cryptocurrency:", cryptoCurrencyAccount.getSingleTradeCurrency().getNameAndCode());
        addAllowedPeriod();
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && altCoinAddressValidator.validate(cryptoCurrencyAccount.getAddress()).isValid
                && cryptoCurrencyAccount.getSingleTradeCurrency() != null);
    }

    @Override
    protected void addTradeCurrencyComboBox() {
        currencyComboBox = addLabelSearchComboBox(gridPane, ++gridRow, "Cryptocurrency:", Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        currencyComboBox.setPromptText("Select or search cryptocurrency");
        currencyComboBox.setItems(FXCollections.observableArrayList(CurrencyUtil.getAllSortedCryptoCurrencies()));
        currencyComboBox.setVisibleRowCount(Math.min(currencyComboBox.getItems().size(), 20));
        currencyComboBox.setConverter(new StringConverter<TradeCurrency>() {
            @Override
            public String toString(TradeCurrency tradeCurrency) {
                return tradeCurrency != null ? tradeCurrency.getNameAndCode() : "";
            }

            @Override
            public TradeCurrency fromString(String s) {
                Optional<TradeCurrency> tradeCurrencyOptional = currencyComboBox.getItems().stream().
                        filter(tradeCurrency -> tradeCurrency.getNameAndCode().equals(s)).
                        findAny();
                if (tradeCurrencyOptional.isPresent())
                    return tradeCurrencyOptional.get();
                else
                    return null;
            }
        });
        currencyComboBox.setOnAction(e -> {
            TradeCurrency selectedItem = currencyComboBox.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                String code = selectedItem.getCode();
                // TODO DAO will be open for sale on 28.5. Check can be removed at next release. 
                if (code.equals("DAO") && new GregorianCalendar().before(new GregorianCalendar(2016, Calendar.MAY, 28))) {
                    new Popup().information("The DAO tokens are not tradable before the pre-sale is over (28th of May).\n" +
                            "From the 28th of May on you can setup a DAO account and trade DAO tokens in Bitsquare.")
                            .closeButtonText("I understand")
                            .onClose(() -> currencyComboBox.getSelectionModel().clearSelection())
                            .show();

                    return;
                }
            }

            paymentAccount.setSingleTradeCurrency(selectedItem);
            updateFromInputs();
        });
    }
}

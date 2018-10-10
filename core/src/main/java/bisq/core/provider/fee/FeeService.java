/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.provider.fee;

import bisq.core.app.BisqEnvironment;

import bisq.common.UserThread;
import bisq.common.handlers.FaultHandler;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;

import com.google.inject.Inject;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

import java.time.Instant;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

// TODO use dao parameters for fee
@Slf4j
public class FeeService {
    // Miner fees are between 1-600 sat/byte. We try to stay on the safe side. BTC_DEFAULT_TX_FEE is only used if our
    // fee service would not deliver data.
    private static final long BTC_DEFAULT_TX_FEE = 50;

    private static long MIN_MAKER_FEE_BTC = 5_000; // 0.005%. 0.5 USD at BTC price 10_000 USD;
    private static long MIN_TAKER_FEE_BTC = 5_000;
    private static long DEFAULT_MAKER_FEE_BTC = 200_000; // 0.2%. 20 USD at BTC price 10000 USD for a 1 BTC trade;
    private static long DEFAULT_TAKER_FEE_BTC = 200_000;

    // 0.05 BSQ (5 satoshi) for a 1 BTC trade -> 0.005%. 0.05 USD if 1 BSQ = 1 USD, 10 % of BTC fee
    private static final long MIN_MAKER_FEE_BSQ = 5;
    private static final long MIN_TAKER_FEE_BSQ = 5;
    // 2 BSQ or 200 BSQ-satoshi. About 2 USD if 1 BSQ = 1 USD for a 1 BTC trade which is about 10% of a normal BTC fee.
    private static final long DEFAULT_TAKER_FEE_BSQ = 200;
    private static final long DEFAULT_MAKER_FEE_BSQ = 200;

    private static final long MIN_PAUSE_BETWEEN_REQUESTS_IN_MIN = 2;

    private final FeeProvider feeProvider;
    private final IntegerProperty feeUpdateCounter = new SimpleIntegerProperty(0);
    private long txFeePerByte = BTC_DEFAULT_TX_FEE;
    private Map<String, Long> timeStampMap;
    private long epochInSecondAtLastRequest;
    private long lastRequest;
    private long minFeePerByte;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public FeeService(FeeProvider feeProvider) {
        this.feeProvider = feeProvider;
    }

    public void onAllServicesInitialized() {
        minFeePerByte = BisqEnvironment.getBaseCurrencyNetwork().getDefaultMinFeePerByte();

        requestFees();

        // We update all 5 min.
        UserThread.runPeriodically(this::requestFees, 5, TimeUnit.MINUTES);
    }

    public void requestFees() {
        requestFees(null, null);
    }

    public void requestFees(Runnable resultHandler) {
        requestFees(resultHandler, null);
    }

    public void requestFees(@Nullable Runnable resultHandler, @Nullable FaultHandler faultHandler) {
        long now = Instant.now().getEpochSecond();
        // We all requests only each 2 minutes
        if (now - lastRequest > MIN_PAUSE_BETWEEN_REQUESTS_IN_MIN * 60) {
            lastRequest = now;
            FeeRequest feeRequest = new FeeRequest();
            SettableFuture<Tuple2<Map<String, Long>, Map<String, Long>>> future = feeRequest.getFees(feeProvider);
            Futures.addCallback(future, new FutureCallback<Tuple2<Map<String, Long>, Map<String, Long>>>() {
                @Override
                public void onSuccess(@Nullable Tuple2<Map<String, Long>, Map<String, Long>> result) {
                    UserThread.execute(() -> {
                        checkNotNull(result, "Result must not be null at getFees");
                        timeStampMap = result.first;
                        epochInSecondAtLastRequest = timeStampMap.get("bitcoinFeesTs");
                        final Map<String, Long> map = result.second;
                        txFeePerByte = map.get("BTC");

                        if (txFeePerByte < minFeePerByte) {
                            log.warn("The delivered fee per byte is smaller than the min. default fee of 5 sat/byte");
                            txFeePerByte = minFeePerByte;
                        }

                        feeUpdateCounter.set(feeUpdateCounter.get() + 1);
                        log.info("BTC tx fee: txFeePerByte={}", txFeePerByte);
                        if (resultHandler != null)
                            resultHandler.run();
                    });
                }

                @Override
                public void onFailure(@NotNull Throwable throwable) {
                    log.warn("Could not load fees. feeProvider={}, error={}", feeProvider.toString(), throwable.toString());
                    if (faultHandler != null)
                        UserThread.execute(() -> faultHandler.handleFault("Could not load fees", throwable));
                }
            });
        } else {
            log.debug("We got a requestFees called again before min pause of {} minutes has passed.", MIN_PAUSE_BETWEEN_REQUESTS_IN_MIN);
            UserThread.execute(() -> {
                if (resultHandler != null)
                    resultHandler.run();
            });
        }
    }

    public Coin getTxFee(int sizeInBytes) {
        return getTxFeePerByte().multiply(sizeInBytes);
    }

    public Coin getTxFeePerByte() {
        return Coin.valueOf(txFeePerByte);
    }

    public static Coin getMakerFeePerBtc(boolean currencyForMakerFeeIsBtc) {
        return currencyForMakerFeeIsBtc ? Coin.valueOf(DEFAULT_MAKER_FEE_BTC) : Coin.valueOf(DEFAULT_MAKER_FEE_BSQ);
    }

    public static Coin getMinMakerFee(boolean currencyForMakerFeeIsBtc) {
        return currencyForMakerFeeIsBtc ? Coin.valueOf(MIN_MAKER_FEE_BTC) : Coin.valueOf(MIN_MAKER_FEE_BSQ);
    }

    public static Coin getTakerFeePerBtc(boolean currencyForTakerFeeIsBtc) {
        return currencyForTakerFeeIsBtc ? Coin.valueOf(DEFAULT_TAKER_FEE_BTC) : Coin.valueOf(DEFAULT_TAKER_FEE_BSQ);
    }

    public static Coin getMinTakerFee(boolean currencyForTakerFeeIsBtc) {
        return currencyForTakerFeeIsBtc ? Coin.valueOf(MIN_TAKER_FEE_BTC) : Coin.valueOf(MIN_TAKER_FEE_BSQ);
    }

    public ReadOnlyIntegerProperty feeUpdateCounterProperty() {
        return feeUpdateCounter;
    }
}

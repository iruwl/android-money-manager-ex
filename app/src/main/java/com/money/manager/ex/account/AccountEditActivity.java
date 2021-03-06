/*
 * Copyright (C) 2012-2016 The Android Money Manager Ex Project Team
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.money.manager.ex.account;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.money.manager.ex.Constants;
import com.money.manager.ex.R;
import com.money.manager.ex.common.events.AmountEnteredEvent;
import com.money.manager.ex.core.Core;
import com.money.manager.ex.core.FormatUtilities;
import com.money.manager.ex.currency.list.CurrencyListActivity;
import com.money.manager.ex.currency.CurrencyRepository;
import com.money.manager.ex.currency.CurrencyService;
import com.money.manager.ex.datalayer.AccountRepository;
import com.money.manager.ex.common.BaseFragmentActivity;
import com.money.manager.ex.common.AmountInputDialog;
import com.money.manager.ex.domainmodel.Account;
import com.money.manager.ex.domainmodel.Currency;
import com.money.manager.ex.settings.AppSettings;
import com.money.manager.ex.view.RobotoTextView;
import com.shamanland.fonticon.FontIconView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.parceler.Parcels;

import java.util.Arrays;

import info.javaperformance.money.Money;

/**
 * Edit Account activity/form
 */
public class AccountEditActivity
    extends BaseFragmentActivity {

    public static final String KEY_ACCOUNT_ENTITY = "AccountEditActivity:AccountEntity";
    public static final String KEY_ACCOUNT_ID = "AccountEditActivity:AccountId";
    public static final String KEY_CURRENCY_NAME = "AccountEditActivity:CurrencyName";
    public static final String KEY_SYMBOL = "AccountEditActivity:Symbol";
    public static final String KEY_DEFAULT_ACCOUNT = "AccountEditActivity:DefaultAccount";
    // LOGCAT
    private static final String LOGCAT = AccountEditActivity.class.getSimpleName();
    // ID REQUEST Data
    private static final int REQUEST_PICK_CURRENCY = 1;
    private static final String KEY_ACTION = "AccountEditActivity:Action";
    // Constant
    private static final int PLUS = 0;
    private static final int MINUS = 1;

    private Account mAccount;

    // Action type
    private String mIntentAction = Intent.ACTION_INSERT; // Insert? Edit?

    // Activity members
    private String mCurrencyName;
    private String[] mAccountTypeValues;
    private String[] mAccountStatusValues;

    private AccountEditViewHolder mViewHolder;
    private boolean mIsDefault;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // create a dummy account for initial values
        mAccount = Account.create("", AccountTypes.CASH, AccountStatuses.OPEN, false, Constants.NOT_SET);

        // Restore saved instance state
        if ((savedInstanceState != null)) {
            restoreInstanceState(savedInstanceState);
        }

        // Get Intent extras
        if (getIntent() != null) {
            if (savedInstanceState == null) {
                mIntentAction = getIntent().getAction();
                if (mIntentAction != null && Intent.ACTION_EDIT.equals(getIntent().getAction())) {
                    int accountId = getIntent().getIntExtra(KEY_ACCOUNT_ID, Constants.NOT_SET);
                    if (accountId != Constants.NOT_SET) {
                        // Load account
                        loadAccount(accountId);
                    }
                }
            }
        }

        // default currency
        if (mAccount.getCurrencyId() == Constants.NOT_SET) {
            CurrencyService currencyService = new CurrencyService(getApplicationContext());
            Currency baseCurrency = currencyService.getBaseCurrency();

            if (baseCurrency != null) {
                mAccount.setCurrencyId(baseCurrency.getCurrencyId());
                mCurrencyName = baseCurrency.getName();
            }
        }

        // Default account
        AppSettings settings = new AppSettings(this);
        Integer defaultAccountId = settings.getGeneralSettings().getDefaultAccountId();
        mIsDefault = mAccount.getId().equals(defaultAccountId);

        // Compose layout
        setContentView(R.layout.activity_edit_account);

        // toolbar
//        if (getToolbar() != null) {
//            showActionBar();
//            showStandardToolbarActions();
//        }
        showStandardToolbarActions();

        initializeControls();
    }

    @Override
    protected void onStart() {
        super.onStart();

        EventBus.getDefault().register(this);
    }

    @Override
    protected void onStop() {
        EventBus.getDefault().unregister(this);

        super.onStop();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_PICK_CURRENCY:
                if ((resultCode == Activity.RESULT_OK) && (data != null)) {
                    int currencyId = data.getIntExtra(CurrencyListActivity.INTENT_RESULT_CURRENCYID, Constants.NOT_SET);
                    mAccount.setCurrencyId(currencyId);

                    mCurrencyName = data.getStringExtra(CurrencyListActivity.INTENT_RESULT_CURRENCYNAME);
                    refreshCurrencyName();

                    // refresh amount
                    Money initialBalance = mAccount.getInitialBalance();
                    if (initialBalance != null) {
                        onEvent(new AmountEnteredEvent("ignored", initialBalance));
                    }
                }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // Get members values from controls
        collectInput();

        // Save the state.

        outState.putParcelable(KEY_ACCOUNT_ENTITY, Parcels.wrap(mAccount));

        outState.putString(KEY_CURRENCY_NAME, mCurrencyName);
        outState.putBoolean(KEY_DEFAULT_ACCOUNT, mIsDefault);
        outState.putString(KEY_ACTION, mIntentAction);
    }

    @Override
    public boolean onActionCancelClick() {
        finish();
//        return super.onActionCancelClick();
        return true;
    }

    @Override
    public boolean onActionDoneClick() {
        if (saveAccount()) {
            // If everything is okay, finish the activity
            finish();
            return true;
        } else {
            return false;
        }
    }

    @Subscribe
    public void onEvent(AmountEnteredEvent event) {
        if (event.amount == null) {
            Log.w(LOGCAT, "Received amount is null.");
            return;
        }

        TextView initialBalanceTextView = (TextView) findViewById(R.id.editTextInitialBalance);

        FormatUtilities formatter = new FormatUtilities(this);
        formatter.formatAmountTextView(initialBalanceTextView, event.amount, mAccount.getCurrencyId());

        mAccount.setInitialBalance(event.amount);
    }

    /**
     * Refresh current currency name on controls
     */
    public void refreshCurrencyName() {
        // write currency into text button
        if (!(TextUtils.isEmpty(mCurrencyName))) {
            mViewHolder.txtSelectCurrency.setText(mCurrencyName);
        } else {
            mViewHolder.txtSelectCurrency.setText(getResources().getString(R.string.select_currency));
        }
    }

    // Private

    private void displayDefaultAccount() {
        mViewHolder.defaultAccountCheckbox.setChecked(mIsDefault);
    }

    private void displayFavouriteStatus() {
        mViewHolder.imageViewAccountFav.setTag(mAccount.getFavorite().toString());

        int imageResource = mAccount.getFavorite()
            ? R.string.ic_star
            : R.string.ic_star_outline;
        mViewHolder.imageViewAccountFav.setText(imageResource);
    }

    private AccountTypes getSelectedAccountType() {
        int accountTypePosition = mViewHolder.accountTypeSpinner.getSelectedItemPosition();
        String accountTypeName = mAccountTypeValues[accountTypePosition];
        AccountTypes accountType = AccountTypes.get(accountTypeName);

        return accountType;
    }

    private void initializeControls() {
        mViewHolder = new AccountEditViewHolder();

        // Get controls from layout
        mViewHolder.edtAccountName = (EditText) findViewById(R.id.editTextAccountName);
        mViewHolder.defaultAccountCheckbox = (CheckBox) findViewById(R.id.defaultAccountCheckbox);
        mViewHolder.defaultAccountText = (RobotoTextView) findViewById(R.id.defaultAccountText);
        mViewHolder.imageViewAccountFav = (FontIconView) findViewById(R.id.imageViewAccountFav);
        mViewHolder.favouriteAccountTextView = (RobotoTextView) findViewById(R.id.favouriteAccountTextView);
        mViewHolder.accountTypeSpinner = (Spinner) findViewById(R.id.spinnerAccountType);
        mViewHolder.edtAccountNumber = (EditText) findViewById(R.id.editTextAccountNumber);
        mViewHolder.edtAccountHeldAt = (EditText) findViewById(R.id.editTextAccountHeldAt);
        mViewHolder.webSiteEditText = (EditText) findViewById(R.id.editTextWebsite);
        mViewHolder.edtContact = (EditText) findViewById(R.id.editTextContact);
        mViewHolder.edtAccessInfo = (EditText) findViewById(R.id.editTextAccessInfo);
        Spinner spinAccountStatus = (Spinner) findViewById(R.id.spinnerAccountStatus);
        mViewHolder.spinSymbolInitialBalance = (Spinner) findViewById(R.id.spinnerSymbolInitialBalance);
        mViewHolder.txtInitialBalance = (TextView) findViewById(R.id.editTextInitialBalance);
        mViewHolder.edtNotes = (EditText) findViewById(R.id.editTextNotes);
        mViewHolder.txtSelectCurrency = (TextView) findViewById(R.id.textViewSelectCurrency);

        // Initial balance.

        ArrayAdapter<String> adapterSymbol = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"+", "-"});
        mViewHolder.spinSymbolInitialBalance.setAdapter(adapterSymbol);

        mViewHolder.txtInitialBalance.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AmountInputDialog dialog = AmountInputDialog.getInstance(null, mAccount.getInitialBalance(), mAccount.getCurrencyId());
                dialog.show(getSupportFragmentManager(), dialog.getClass().getSimpleName());
            }
        });

        // Account Type adapters and values

        String[] mAccountTypeItems = getResources().getStringArray(R.array.accounttype_items);
        mAccountTypeValues = AccountTypes.getNames();
        ArrayAdapter<String> adapterAccountType = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, mAccountTypeItems);
        adapterAccountType.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mViewHolder.accountTypeSpinner.setAdapter(adapterAccountType);
        if (!(TextUtils.isEmpty(mAccount.getTypeName()))) {
            if (Arrays.asList(mAccountTypeValues).indexOf(mAccount.getTypeName()) >= 0) {
                int position = Arrays.asList(mAccountTypeValues).indexOf(mAccount.getTypeName());
                mViewHolder.accountTypeSpinner.setSelection(position, true);
            }
        } else {
            AccountTypes accountType = getSelectedAccountType();
            mAccount.setType(accountType);
        }

        // Account Status adapters and values

        String[] mAccountStatusItems = getResources().getStringArray(R.array.accountstatus_items);
        mAccountStatusValues = getResources().getStringArray(R.array.accountstatus_values);
        ArrayAdapter<String> adapterAccountStatus = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, mAccountStatusItems);
        spinAccountStatus.setAdapter(adapterAccountStatus);
        adapterAccountStatus.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (!(TextUtils.isEmpty(mAccount.getStatus()))) {
            if (Arrays.asList(mAccountStatusValues).indexOf(mAccount.getStatus()) >= 0) {
                spinAccountStatus.setSelection(Arrays.asList(mAccountStatusValues).indexOf(mAccount.getStatus()), true);
            }
        } else {
            String selectedStatus = (String) spinAccountStatus.getSelectedItem();
            AccountStatuses status = AccountStatuses.get(selectedStatus);
            mAccount.setStatus(status);
        }

        // Set up control listeners

        initializeDefaultAccountControls();

        mViewHolder.accountTypeSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if ((position >= 0) && (position <= mAccountTypeValues.length)) {
                    //ArrayAdapter<String> adapter = (ArrayAdapter<String>) parent.getAdapter();
                    String accountTypeValue = mAccountTypeValues[position];
                    AccountTypes accountType = AccountTypes.get(accountTypeValue);
                    mAccount.setType(accountType);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        spinAccountStatus.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if ((position >= 0) && (position <= mAccountStatusValues.length)) {
                    String selectedStatus = mAccountStatusValues[position];
                    AccountStatuses status = AccountStatuses.get(selectedStatus);
                    mAccount.setStatus(status);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Favourite
        initializeFavouriteAccountControls();

        mViewHolder.txtSelectCurrency.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(AccountEditActivity.this, CurrencyListActivity.class);
                intent.setAction(Intent.ACTION_PICK);
                startActivityForResult(intent, REQUEST_PICK_CURRENCY);
            }
        });

        // Display data.

        displayAccountValues();

        // Refresh data on the other controls
        refreshCurrencyName();
    }

    private void displayAccountValues() {
        if (!(TextUtils.isEmpty(mAccount.getName()))) {
            mViewHolder.edtAccountName.setText(mAccount.getName());
        }

        // Default account.
        displayDefaultAccount();

        // Favourite account.
        displayFavouriteStatus();

        // Initial balance
        mViewHolder.spinSymbolInitialBalance.setSelection(mAccount.getInitialBalance().toDouble() >= 0 ? PLUS : MINUS);
        // always use positive numeric value. The sign is in the spinner.
        if (mAccount.getInitialBalance().toDouble() < 0) {
            mAccount.setInitialBalance(mAccount.getInitialBalance().negate());
        }

        FormatUtilities formatter = new FormatUtilities(this);
        formatter.formatAmountTextView(mViewHolder.txtInitialBalance, mAccount.getInitialBalance(), mAccount.getCurrencyId());

        // Account Number
        if (!(TextUtils.isEmpty(mAccount.getAccountNumber()))) {
            mViewHolder.edtAccountNumber.setText(mAccount.getAccountNumber());
        }
        if (!(TextUtils.isEmpty(mAccount.getHeldAt()))) {
            mViewHolder.edtAccountHeldAt.setText(mAccount.getHeldAt());
        }
        if (!(TextUtils.isEmpty(mAccount.getWebSite()))) {
            mViewHolder.webSiteEditText.setText(mAccount.getWebSite());
        }
        if (!(TextUtils.isEmpty(mAccount.getContactInfo()))) {
            mViewHolder.edtContact.setText(mAccount.getContactInfo());
        }
        if (!(TextUtils.isEmpty(mAccount.getAccessInfo()))) {
            mViewHolder.edtAccessInfo.setText(mAccount.getAccessInfo());
        }
        // Notes
        if (!(TextUtils.isEmpty(mAccount.getNotes()))) {
            mViewHolder.edtNotes.setText(mAccount.getNotes());
        }

    }

    private void initializeDefaultAccountControls() {
        mViewHolder.defaultAccountCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mIsDefault = isChecked;

                displayDefaultAccount();
            }
        });

        mViewHolder.defaultAccountText.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsDefault = !mIsDefault;

                displayDefaultAccount();
            }
        });
    }

    private void initializeFavouriteAccountControls() {
        OnClickListener listener = new OnClickListener() {

            @Override
            public void onClick(View v) {
                mAccount.setFavorite(!mAccount.getFavorite());
                displayFavouriteStatus();
            }
        };
        mViewHolder.imageViewAccountFav.setOnClickListener(listener);
        mViewHolder.favouriteAccountTextView.setOnClickListener(listener);
    }

    /**
     * Validate entered data.
     */
    private boolean validateData() {
        if (mAccount.getCurrencyId() == null || mAccount.getCurrencyId() == Constants.NOT_SET) {
            Core.alertDialog(this, R.string.error_currency_not_selected);
            return false;
        }
        if (!mAccount.hasInitialBalance()) {
            Core.alertDialog(this, R.string.error_initialbal_empty);
            return false;
        }
        if (TextUtils.isEmpty(mAccount.getName())) {
            Core.alertDialog(this, R.string.error_accountname_empty);
            return false;
        }
        if (TextUtils.isEmpty(mAccount.getTypeName())) {
            Core.alertDialog(this, R.string.error_accounttype_empty);
            return false;
        }
        if (TextUtils.isEmpty(mAccount.getStatus())) {
            Core.alertDialog(this, R.string.error_status_empty);
            return false;
        }

        // TODO: Should throw an exception in case favoriteacct is not in {'TRUE', 'FALSE'}
        return true;
    }

    /**
     * Transfer data from UI to the model.
     * Replace with data binding later.
     */
    private void collectInput() {
        mAccount.setName(mViewHolder.edtAccountName.getText().toString());

        AccountTypes accountType = getSelectedAccountType();
        mAccount.setType(accountType);

        mAccount.setAccountNumber(mViewHolder.edtAccountNumber.getText().toString());
        mAccount.setHeldAt(mViewHolder.edtAccountHeldAt.getText().toString());
        mAccount.setWebSite(mViewHolder.webSiteEditText.getText().toString());
        mAccount.setContactInfo(mViewHolder.edtContact.getText().toString());
        mAccount.setAccessInfo(mViewHolder.edtAccessInfo.getText().toString());
        mAccount.setNotes(mViewHolder.edtNotes.getText().toString());

        if (mViewHolder.spinSymbolInitialBalance.getSelectedItemPosition() != PLUS) {
            Money initialBalance = mAccount.getInitialBalance();
            initialBalance = initialBalance.negate();
            mAccount.setInitialBalance(initialBalance);
        }
    }

    /**
     * update data into database
     * @return true if update data successful
     */
    private boolean saveAccount() {
        collectInput();

        if (!validateData()) {
            return false;
        }

        AccountRepository repo = new AccountRepository(this);
        repo.save(mAccount);

        saveDefaultAccount();

        return true;
    }

    /**
     * Select the account identified by accountId
     *
     * @param accountId account id
     * @return true if data is correctly selected, false if error occurs
     */
    private boolean loadAccount(int accountId) {
        AccountRepository repository = new AccountRepository(getApplicationContext());
        mAccount = repository.load(accountId);
        if (mAccount == null) return false;

        // TODO Select currency name: could be improved for better usage of members
        selectCurrencyName(mAccount.getCurrencyId());

        return true;
    }

    private void restoreInstanceState(Bundle savedInstanceState) {
        mAccount = Parcels.unwrap(savedInstanceState.getParcelable(KEY_ACCOUNT_ENTITY));

        if (savedInstanceState.getInt(KEY_SYMBOL) == MINUS) {
            mAccount.setInitialBalance(mAccount.getInitialBalance().negate());
        }

        mCurrencyName = savedInstanceState.getString(KEY_CURRENCY_NAME);
        mIsDefault = savedInstanceState.getBoolean(KEY_DEFAULT_ACCOUNT);
        mIntentAction = savedInstanceState.getString(KEY_ACTION);
    }

    /**
     * Query info for current currency
     *
     * @param currencyId Id of the currency to select
     * @return A boolean indicating whether the retrieval of currency name was successful.
     */
    private boolean selectCurrencyName(int currencyId) {
        boolean result;
        CurrencyRepository repository = new CurrencyRepository(getApplicationContext());
        Currency currency = repository.loadCurrency(currencyId);
        if (currency == null) {
            mCurrencyName = "N/A";
            result = false;
        } else {
            mCurrencyName = currency.getName();
            result = true;
        }

        return result;
    }

    private void saveDefaultAccount() {
        AppSettings settings = new AppSettings(AccountEditActivity.this);

        if (mIsDefault) {
            // set this account as default.
            settings.getGeneralSettings().setDefaultAccountId(mAccount.getId());
        } else {
            // Check if this was the default account and is now being unset.
            Integer currentDefaultAccountId = settings.getGeneralSettings().getDefaultAccountId();
            if (currentDefaultAccountId != null && currentDefaultAccountId.equals(mAccount.getId())) {
                // Reset default account.
                settings.getGeneralSettings().setDefaultAccountId(null);
            }
        }
    }
}

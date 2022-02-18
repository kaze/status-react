import random
import string

from tests import marks, unique_password
from tests.base_test_case import SingleDeviceTestCase, MultipleDeviceTestCase
from tests.users import transaction_senders, basic_user, wallet_users, ens_user_ropsten, transaction_recipients
from views.send_transaction_view import SendTransactionView
from views.sign_in_view import SignInView


class TestTransactionWalletSingleDevice(SingleDeviceTestCase):

    @marks.testrail_id(6208)
    @marks.high
    @marks.transaction
    def test_send_transaction_with_custom_token(self):
        contract_address, name, symbol, decimals = '0x101848D5C5bBca18E6b4431eEdF6B95E9ADF82FA', 'Weenus 💪', 'WEENUS', '18'
        home = SignInView(self.driver).recover_access(wallet_users['B']['passphrase'])
        wallet = home.wallet_button.click()
        wallet.multiaccount_more_options.click()
        wallet.manage_assets_button.click()
        token_view = wallet.add_custom_token_button.click()
        token_view.contract_address_input.send_keys(contract_address)
        if token_view.name_input.text != name:
            self.errors.append('Name for custom token was not set')
        if token_view.symbol_input.text != symbol:
            self.errors.append('Symbol for custom token was not set')
        if token_view.decimals_input.text != decimals:
            self.errors.append('Decimals for custom token was not set')
        token_view.add_button.click()
        token_view.close_button.click()
        wallet.asset_by_name(symbol).scroll_to_element()
        if not wallet.asset_by_name(symbol).is_element_displayed():
            self.errors.append('Custom token is not shown on Wallet view')
        recipient = "0x" + basic_user['address']
        amount = '0.000%s' % str(random.randint(10000, 99999)) + '1'
        wallet.send_transaction(asset_name=symbol, amount=amount, recipient=recipient)
        # TODO: disabled due to 10838 (rechecked 23.11.21, valid)
        # transactions_view = wallet.transaction_history_button.click()
        # transactions_view.transactions_table.find_transaction(amount=amount, asset=symbol)

        self.errors.verify_no_errors()

    @marks.testrail_id(5350)
    @marks.critical
    @marks.transaction
    def test_send_token_with_7_decimals(self):
        sender, recipient = transaction_senders['S'], basic_user
        home = SignInView(self.driver).recover_access(sender['passphrase'])
        wallet = home.wallet_button.click()
        wallet.wait_balance_is_changed(asset='ADI', scan_tokens=True)
        amount = '0.000%s' % str(random.randint(100, 999)) + '1'
        wallet.send_transaction(amount=amount, recipient='0x%s' % recipient['address'], asset_name='ADI')
        transaction = wallet.find_transaction_in_history(amount=amount, asset='ADI', return_hash=True)
        self.network_api.find_transaction_by_hash(transaction)

    @marks.testrail_id(5308)
    @marks.critical
    @marks.transaction
    def test_send_eth_from_wallet_to_address_incorrect_password(self):
        recipient = basic_user
        sender = transaction_senders['P']
        sign_in = SignInView(self.driver)
        home = sign_in.recover_access(sender['passphrase'], password=unique_password)
        wallet = home.wallet_button.click()
        wallet.accounts_status_account.click()
        transaction_amount = wallet.get_unique_amount()

        wallet.just_fyi("Checking that can't send transaction with wrong password")
        send_transaction = wallet.send_transaction_button.click()
        send_transaction.amount_edit_box.click()
        send_transaction.amount_edit_box.set_value(transaction_amount)
        send_transaction.set_recipient_address('0x%s' % recipient['address'])
        send_transaction.sign_transaction_button.click()
        send_transaction.sign_with_password.click_until_presence_of_element(send_transaction.enter_password_input)
        send_transaction.enter_password_input.click()
        send_transaction.enter_password_input.send_keys('wrong_password')
        send_transaction.sign_button.click()
        if send_transaction.element_by_text_part('Transaction sent').is_element_displayed():
            self.driver.fail('Transaction was sent with a wrong password')

        wallet.just_fyi("Fix password and sign transaction")
        send_transaction.enter_password_input.clear()
        send_transaction.enter_password_input.send_keys(unique_password)
        send_transaction.sign_button.click()
        wallet.ok_button.wait_and_click()

        wallet.just_fyi('Check that transaction is appeared in transaction history')
        wallet.find_transaction_in_history(amount=transaction_amount)

        wallet.just_fyi('Check logcat for sensitive data')
        values_in_logcat = wallet.find_values_in_logcat(password=unique_password)
        if values_in_logcat:
            self.driver.fail(values_in_logcat)

    @marks.testrail_id(6237)
    @marks.high
    def test_fetching_balance_after_offline(self):
        sender = wallet_users['E']
        sign_in = SignInView(self.driver)

        sign_in.just_fyi('Checking if balance will be restored after going back online')
        sign_in.toggle_airplane_mode()
        home = sign_in.recover_access(sender['passphrase'])
        sign_in.toggle_airplane_mode()
        wallet = home.wallet_button.click()
        [wallet.wait_balance_is_changed(asset) for asset in ("ETH", "STT")]
        self.driver.reset()

        sign_in.just_fyi('Keycard: checking if balance will be restored after going back online')
        self.driver.close_app()
        sign_in.toggle_airplane_mode()
        self.driver.launch_app()
        sign_in.recover_access(sender['passphrase'], keycard=True)
        sign_in.toggle_airplane_mode()
        wallet = home.wallet_button.click()
        [wallet.wait_balance_is_changed(asset) for asset in ("ETH", "STT")]

    @marks.testrail_id(5412)
    @marks.high
    def test_insufficient_funds_wallet_positive_balance(self):
        sender = wallet_users['E']
        home = SignInView(self.driver).recover_access(sender['passphrase'])
        wallet = home.wallet_button.click()
        [wallet.wait_balance_is_changed(asset) for asset in ['ETH', 'STT']]
        eth_value, stt_value = wallet.get_asset_amount_by_name('ETH'), wallet.get_asset_amount_by_name('STT')
        if eth_value == 0 or stt_value == 0:
            self.driver.fail('No funds!')
        wallet.accounts_status_account.click()
        send_transaction = wallet.send_transaction_button.click()
        send_transaction.amount_edit_box.set_value(round(eth_value + 1))
        error_text = send_transaction.element_by_text('Insufficient funds')
        if not error_text.is_element_displayed():
            self.errors.append(
                "'Insufficient funds' error is not shown when sending %s ETH from wallet with balance %s" % (
                    round(eth_value + 1), eth_value))
        send_transaction.select_asset_button.click()
        send_transaction.asset_by_name('STT').scroll_to_element()
        send_transaction.asset_by_name('STT').click()
        send_transaction.amount_edit_box.set_value(round(stt_value + 1))
        if not error_text.is_element_displayed():
            self.errors.append(
                "'Insufficient funds' error is not shown when sending %s STT from wallet with balance %s" % (
                    round(stt_value + 1), stt_value))
        self.errors.verify_no_errors()

    @marks.testrail_id(5429)
    @marks.medium
    def test_set_currency(self):
        home = SignInView(self.driver).create_user()
        user_currency = 'Euro (EUR)'
        wallet = home.wallet_button.click()
        wallet.set_currency(user_currency)
        if not wallet.element_by_text_part('EUR').is_element_displayed(20):
            self.driver.fail('EUR currency is not displayed')

    @marks.testrail_id(5358)
    @marks.medium
    def test_backup_recovery_phrase_warning_from_wallet(self):
        sign_in = SignInView(self.driver)
        sign_in.create_user()
        wallet = sign_in.wallet_button.click()
        if wallet.backup_recovery_phrase_warning_text.is_element_present():
            self.driver.fail("'Back up your seed phrase' warning is shown on Wallet while no funds are present")
        address = wallet.get_wallet_address()
        self.network_api.get_donate(address[2:], external_faucet=True, wait_time=200)
        wallet.close_button.click()
        wallet.wait_balance_is_changed(scan_tokens=True)
        if not wallet.backup_recovery_phrase_warning_text.is_element_present(30):
            self.driver.fail("'Back up your seed phrase' warning is not shown on Wallet with funds")
        profile = wallet.get_profile_view()
        wallet.backup_recovery_phrase_warning_text.click()
        profile.backup_recovery_phrase()

    @marks.testrail_id(5407)
    @marks.medium
    @marks.transaction
    def test_offline_can_login_cant_send_transaction(self):
        home = SignInView(self.driver).create_user()
        wallet = home.wallet_button.click()
        wallet.toggle_airplane_mode()
        wallet.accounts_status_account.click_until_presence_of_element(wallet.send_transaction_button)
        send_transaction = wallet.send_transaction_button.click()
        send_transaction.set_recipient_address('0x%s' % basic_user['address'])
        send_transaction.amount_edit_box.set_value("0")
        send_transaction.confirm()
        send_transaction.sign_transaction_button.click()
        if send_transaction.sign_with_password.is_element_displayed():
            self.driver.fail("Sign transaction button is active in offline mode")
        self.driver.close_app()
        self.driver.launch_app()
        SignInView(self.driver).sign_in()
        home.home_button.wait_for_visibility_of_element()
        home.connection_offline_icon.wait_for_visibility_of_element(20)

    @marks.testrail_id(6225)
    @marks.transaction
    @marks.medium
    def test_send_funds_between_accounts_in_multiaccount_instance(self):
        sign_in = SignInView(self.driver)
        sign_in.create_user()
        wallet = sign_in.wallet_button.click()
        status_account_address = wallet.get_wallet_address()[2:]
        self.network_api.get_donate(status_account_address, external_faucet=True)
        wallet.wait_balance_is_changed()

        account_name = 'subaccount'
        wallet.add_account(account_name)

        wallet.just_fyi("Send transaction to new account")
        initial_balance = self.network_api.get_balance(status_account_address)

        transaction_amount = '0.003%s' % str(random.randint(10000, 99999)) + '1'
        wallet.send_transaction(account_name=account_name, amount=transaction_amount)
        self.network_api.wait_for_confirmation_of_transaction(status_account_address, transaction_amount)
        self.network_api.verify_balance_is_updated(str(initial_balance), status_account_address)

        wallet.just_fyi("Verifying previously sent transaction in new account")
        wallet.get_account_by_name(account_name).click()
        wallet.send_transaction_button.click()
        wallet.close_send_transaction_view_button.click()
        balance_after_receiving_tx = float(wallet.get_asset_amount_by_name('ETH'))
        expected_balance = self.network_api.get_rounded_balance(balance_after_receiving_tx, transaction_amount)
        if balance_after_receiving_tx != expected_balance:
            self.driver.fail('New account balance %s does not match expected %s after receiving a transaction' % (
                balance_after_receiving_tx, transaction_amount))

        wallet.just_fyi("Sending eth from new account to main account")
        updated_balance = self.network_api.get_balance(status_account_address)
        transaction_amount_1 = round(float(transaction_amount) * 0.2, 12)
        wallet.send_transaction(from_main_wallet=False, account_name=wallet.status_account_name,
                                amount=transaction_amount_1)
        wallet.close_button.click()
        sub_account_address = wallet.get_wallet_address(account_name)[2:]
        self.network_api.wait_for_confirmation_of_transaction(status_account_address, transaction_amount_1)
        self.network_api.verify_balance_is_updated(updated_balance, status_account_address)
        wallet.find_transaction_in_history(amount=transaction_amount)
        wallet.find_transaction_in_history(amount=format(float(transaction_amount_1), '.11f').rstrip('0'))

        wallet.just_fyi("Check transactions on subaccount")
        self.network_api.verify_balance_is_updated(updated_balance, status_account_address)

        wallet.just_fyi("Verify total ETH on main wallet view")
        self.network_api.wait_for_confirmation_of_transaction(status_account_address, transaction_amount_1)
        self.network_api.verify_balance_is_updated((updated_balance + transaction_amount_1), status_account_address)
        wallet.close_button.click()
        balance_of_sub_account = float(self.network_api.get_balance(sub_account_address)) / 1000000000000000000
        balance_of_status_account = float(self.network_api.get_balance(status_account_address)) / 1000000000000000000
        wallet.scan_tokens()
        total_eth_from_two_accounts = float(wallet.get_asset_amount_by_name('ETH'))
        expected_balance = self.network_api.get_rounded_balance(total_eth_from_two_accounts,
                                                                (balance_of_status_account + balance_of_sub_account))

        if total_eth_from_two_accounts != expected_balance:
            self.driver.fail('Total wallet balance %s != of Status account (%s) + SubAccount (%s)' % (
                total_eth_from_two_accounts, balance_of_status_account, balance_of_sub_account))

    @marks.testrail_id(6235)
    @marks.medium
    def test_can_change_account_settings(self):
        sign_in_view = SignInView(self.driver)
        sign_in_view.create_user()
        wallet_view = sign_in_view.wallet_button.click()
        status_account_address = wallet_view.get_wallet_address()
        wallet_view.get_account_options_by_name().click()

        wallet_view.just_fyi('open Account Settings screen and check that all elements are shown')
        wallet_view.account_settings_button.click()
        for text in 'On Status tree', status_account_address, "m/44'/60'/0'/0/0":
            if not wallet_view.element_by_text(text).is_element_displayed():
                self.errors.append("'%s' text is not shown on Account Settings screen!" % text)

        wallet_view.just_fyi('change account name/color and verified applied changes')
        account_name = ''.join(random.choice(string.ascii_uppercase + string.digits) for _ in range(10))
        wallet_view.account_name_input.clear()
        wallet_view.account_name_input.send_keys(account_name)
        wallet_view.account_color_button.select_color_by_position(1)
        wallet_view.apply_settings_button.click()
        wallet_view.element_by_text('This device').scroll_to_element()
        wallet_view.close_button.click()
        wallet_view.close_button.click()
        account_button = wallet_view.get_account_by_name(account_name)
        if not account_button.is_element_displayed():
            self.driver.fail('Account name was not changed')
        if not account_button.color_matches('multi_account_color.png'):
            self.driver.fail('Account color does not match expected')

        self.errors.verify_no_errors()

    @marks.testrail_id(6282)
    @marks.medium
    def test_can_scan_eip_681_links(self):
        sign_in = SignInView(self.driver)
        sign_in.recover_access(transaction_senders['C']['passphrase'])
        wallet = sign_in.wallet_button.click()
        wallet.wait_balance_is_changed()
        send_transaction_view = SendTransactionView(self.driver)

        sign_in.just_fyi("Setting up wallet")
        wallet.accounts_status_account.click_until_presence_of_element(wallet.send_transaction_button)
        send_transaction = wallet.send_transaction_button.click()
        send_transaction.set_recipient_address('0x%s' % basic_user['address'])
        send_transaction.amount_edit_box.set_value("0")
        send_transaction.confirm()
        send_transaction.sign_transaction_button.click()
        wallet.set_up_wallet_when_sending_tx()
        wallet.cancel_button.click()
        wallet.close_button.click()

        url_data = {
            'ens_for_receiver': {
                'url': 'ethereum:0xc55cf4b03948d7ebc8b9e8bad92643703811d162@3/transfer?address=nastya.stateofus.eth&uint256=1e-1',
                'data': {
                    'asset': 'STT',
                    'amount': '0.1',
                    'address': '0x58d8…F2ff',
                },
            },
            # 'gas_settings': {
            #     'url': 'ethereum:0x3d597789ea16054a084ac84ce87f50df9198f415@3?value=1e16&gasPrice=1000000000&gasLimit=100000',
            #     'data': {
            #         'amount': '0.01',
            #         'asset': 'ETHro',
            #         'address': '0x3D59…F415',
            #         'gas_limit': '100000',
            #         'gas_price': '1',
            #     },
            # },
            'payment_link': {
                'url': 'ethereum:pay-0xc55cf4b03948d7ebc8b9e8bad92643703811d162@3/transfer?address=0x3d597789ea16054a084ac84ce87f50df9198f415&uint256=1e1',
                'data': {
                    'amount': '10',
                    'asset': 'STT',
                    'address': '0x3D59…F415',
                },
            },
            'validation_amount_too_presize': {
                'url': 'ethereum:0xc55cf4b03948d7ebc8b9e8bad92643703811d162@3/transfer?address=0x101848D5C5bBca18E6b4431eEdF6B95E9ADF82FA&uint256=1e-19',
                'data': {
                    'amount': '1e-19',
                    'asset': 'STT',
                    'address': '0x1018…82FA',

                },
                'send_transaction_validation_error': 'Amount is too precise',
            },
            'validation_amount_too_big': {
                'url': 'ethereum:0x101848D5C5bBca18E6b4431eEdF6B95E9ADF82FA@3?value=1e25',
                'data': {
                    'amount': '10000000',
                    'asset': 'ETHro',
                    'address': '0x1018…82FA',

                },
                'send_transaction_validation_error': 'Insufficient funds',
            },
            'validation_wrong_chain_id': {
                'url': 'ethereum:0x101848D5C5bBca18E6b4431eEdF6B95E9ADF82FA?value=1e17',
                'error': 'Network does not match',
                'data': {
                    'amount': '0.1',
                    'asset': 'ETHro',
                    'address': '0x1018…82FA',
                },
            },
            'validation_wrong_address': {
                'url': 'ethereum:0x744d70fdbe2ba4cf95131626614a1763df805b9e@3/transfer?address=blablabla&uint256=1e10',
                'error': 'Invalid address',
            },
        }

        for key in url_data:
            wallet.just_fyi('Checking %s case' % key)
            wallet.scan_qr_button.click()
            if wallet.allow_button.is_element_displayed():
                wallet.allow_button.click()
            wallet.enter_qr_edit_box.scan_qr(url_data[key]['url'])
            if url_data[key].get('error'):
                if not wallet.element_by_text_part(url_data[key]['error']).is_element_displayed():
                    self.errors.append('Expected error %s is not shown' % url_data[key]['error'])
                wallet.ok_button.click()
            if url_data[key].get('data'):
                actual_data = send_transaction_view.get_values_from_send_transaction_bottom_sheet()
                difference_in_data = url_data[key]['data'].items() - actual_data.items()
                if difference_in_data:
                    self.errors.append(
                        'In %s case returned value does not match expected in %s' % (key, repr(difference_in_data)))
                if url_data[key].get('send_transaction_validation_error'):
                    error = url_data[key]['send_transaction_validation_error']
                    if not wallet.element_by_text_part(error).is_element_displayed():
                        self.errors.append(
                            'Expected error %s is not shown' % error)
                if wallet.close_send_transaction_view_button.is_element_displayed():
                    wallet.close_send_transaction_view_button.wait_and_click()
                else:
                    wallet.cancel_button.wait_and_click()

        self.errors.verify_no_errors()

    @marks.testrail_id(5437)
    @marks.medium
    def test_validation_amount_errors(self):
        sender = wallet_users['C']
        sign_in = SignInView(self.driver)

        errors = {'send_transaction_screen': {
            'too_precise': 'Amount is too precise. Max number of decimals is 7.',
            'insufficient_funds': 'Insufficient funds'
        },
            'sending_screen': {
                'Amount': 'Insufficient funds',
                'Network fee': 'Not enough ETH for gas'
            },
        }
        warning = 'Warning %s is not shown on %s'

        sign_in.recover_access(sender['passphrase'])
        wallet = sign_in.wallet_button.click()
        wallet.wait_balance_is_changed('ADI')
        wallet.accounts_status_account.click()

        screen = 'send transaction screen from wallet'
        sign_in.just_fyi('Checking %s on %s' % (errors['send_transaction_screen']['too_precise'], screen))
        initial_amount_adi = wallet.get_asset_amount_by_name('ADI')
        send_transaction = wallet.send_transaction_button.click()
        adi_button = send_transaction.asset_by_name('ADI')
        send_transaction.select_asset_button.click_until_presence_of_element(
            send_transaction.eth_asset_in_select_asset_bottom_sheet_button)
        adi_button.click()
        send_transaction.amount_edit_box.click()
        amount = '0.000%s' % str(random.randint(100000, 999999)) + '1'
        send_transaction.amount_edit_box.set_value(amount)
        if not send_transaction.element_by_text(
                errors['send_transaction_screen']['too_precise']).is_element_displayed():
            self.errors.append(warning % (errors['send_transaction_screen']['too_precise'], screen))

        sign_in.just_fyi('Checking %s on %s' % (errors['send_transaction_screen']['insufficient_funds'], screen))
        send_transaction.amount_edit_box.clear()
        send_transaction.amount_edit_box.set_value(str(initial_amount_adi) + '1')
        if not send_transaction.element_by_text(
                errors['send_transaction_screen']['insufficient_funds']).is_element_displayed():
            self.errors.append(warning % (errors['send_transaction_screen']['insufficient_funds'], screen))
        wallet.close_send_transaction_view_button.click()
        wallet.close_button.click()

        screen = 'sending screen from wallet'
        sign_in.just_fyi('Checking %s on %s' % (errors['sending_screen']['Network fee'], screen))
        account_name = 'new'
        wallet.add_account(account_name)
        wallet.get_account_by_name(account_name).click()
        wallet.send_transaction_button.click()
        send_transaction.amount_edit_box.set_value('0')
        send_transaction.set_recipient_address(ens_user_ropsten['ens'])
        send_transaction.next_button.click()
        wallet.ok_got_it_button.wait_and_click(30)
        if not send_transaction.validation_error_element.is_element_displayed(10):
            self.errors.append('Validation icon is not shown when testing %s on %s' % (errors['sending_screen']['Network fee'], screen))
        if not wallet.element_by_translation_id("tx-fail-description2").is_element_displayed():
            self.errors.append("No warning about failing tx is shown!")
        send_transaction.cancel_button.click()

        screen = 'sending screen from DApp'
        sign_in.just_fyi('Checking %s on %s' % (errors['sending_screen']['Network fee'], screen))
        home = wallet.home_button.click()
        dapp = sign_in.dapp_tab_button.click()
        dapp.select_account_button.click()
        dapp.select_account_by_name(account_name).wait_for_element(30)
        dapp.select_account_by_name(account_name).click()
        status_test_dapp = home.open_status_test_dapp()
        status_test_dapp.wait_for_d_aap_to_load()
        status_test_dapp.transactions_button.click_until_presence_of_element(
            status_test_dapp.send_two_tx_in_batch_button)
        status_test_dapp.send_two_tx_in_batch_button.click()
        if not send_transaction.validation_error_element.is_element_displayed(10):
            self.errors.append(warning % (errors['sending_screen']['Network fee'], screen))
        self.errors.verify_no_errors()

    @marks.testrail_id(695855)
    @marks.transaction
    @marks.medium
    def test_custom_gas_settings(self):
        sender = wallet_users['B']
        sign_in = SignInView(self.driver)
        sign_in.recover_access(sender['passphrase'])
        wallet = sign_in.wallet_button.click()
        wallet.wait_balance_is_changed()
        wallet.accounts_status_account.click()

        send_transaction = wallet.send_transaction_button.click()
        amount = '0.000%s' % str(random.randint(100000, 999999)) + '1'
        send_transaction.amount_edit_box.set_value(amount)
        send_transaction.set_recipient_address(ens_user_ropsten['ens'])
        send_transaction.next_button.click()
        wallet.ok_got_it_button.wait_and_click(30)
        send_transaction.network_fee_button.click()
        send_transaction = wallet.get_send_transaction_view()
        fee_fields = (send_transaction.per_gas_tip_limit_input, send_transaction.per_gas_price_limit_input)
        [default_tip, default_price] = [field.text for field in fee_fields]
        default_limit = '21000'

        wallet.just_fyi("Check basic validation")
        values = {
            send_transaction.gas_limit_input:
                {
                    'default': default_limit,
                    'value': '22000',
                    '20999': 'wallet-send-min-units',
                    '@!': 'invalid-number',
                },
            send_transaction.per_gas_tip_limit_input:
                {
                    'default': default_tip,
                    'value': '2.5',
                    'aaaa': 'invalid-number',
                },
            send_transaction.per_gas_price_limit_input:
                {
                    'default': default_price,
                    'value': str(round(float(default_price)+3, 9)),
                    '-2': 'invalid-number',
                }
        }
        for field in values:
            for key in values[field]:
                if key != 'default' and key != 'value':
                    field.clear()
                    field.send_keys(key)
                    if not send_transaction.element_by_translation_id(values[field][key]).is_element_displayed(10):
                        self.errors.append("%s is not shown for %s" % (values[field][key], field.accessibility_id))
                    field.clear()
                    field.set_value(values[field]['value'])

        wallet.just_fyi("Set custom fee and check that it will be applied")
        send_transaction.save_fee_button.scroll_and_click()
        if wallet.element_by_translation_id("change-tip").is_element_displayed():
            wallet.element_by_translation_id("continue-anyway").click()
        send_transaction.sign_transaction()
        self.network_api.wait_for_confirmation_of_transaction(sender['address'], amount, confirmations=3)
        transaction = wallet.find_transaction_in_history(amount=amount, return_hash=True)
        expected_params = {
            'fee_cap': values[send_transaction.per_gas_price_limit_input]['value'],
            'tip_cap': '2.5',
            'gas_limit': '22000'
        }
        actual_params = self.network_api.get_custom_fee_tx_params(transaction)
        if actual_params != expected_params:
            self.errors.append('Real params %s for tx do not match expected %s' % (str(actual_params), str(expected_params)))

        wallet.just_fyi('Verify custom fee data on tx screen')
        wallet.swipe_up()
        for key in expected_params:
            if not wallet.element_by_text_part(expected_params[key]).is_element_displayed():
                self.errors.append("Custom tx param %s is not shown on tx history screen" % key)

        wallet.just_fyi("Check below fee popup on mainnet")
        profile = wallet.profile_button.click()
        profile.switch_network()
        sign_in.wallet_button.click()
        wallet.accounts_status_account.click()

        send_transaction = wallet.send_transaction_button.click_until_presence_of_element(send_transaction.amount_edit_box)
        send_transaction.amount_edit_box.set_value(0)
        send_transaction.set_recipient_address(ens_user_ropsten['ens'])
        send_transaction.next_button.click()
        wallet.element_by_translation_id("network-fee").click()
        if not wallet.element_by_translation_id("tx-fail-description2").is_element_displayed():
            self.errors.append("Tx is likely to fail is not shown!")
        if send_transaction.network_fee_button.is_element_displayed():
            self.errors.append("Still can set tx fee when balance is not enough")

        ##  TODO: should be moved to another test after 8f52b9b63ccd9a52b7fe37ab4f89a2e7b6721fcd
        # send_transaction = wallet.get_send_transaction_view()
        # send_transaction.gas_limit_input.clear()
        # send_transaction.gas_limit_input.set_value(default_limit)
        # send_transaction.per_gas_price_limit_input.clear()
        # send_transaction.per_gas_price_limit_input.click()
        # send_transaction.per_gas_price_limit_input.send_keys('1')
        # if not wallet.element_by_translation_id("below-base-fee").is_element_displayed(10):
        #     self.errors.append("Fee is below error is not shown")
        # send_transaction.save_fee_button.scroll_and_click()
        # if not wallet.element_by_translation_id("change-tip").is_element_displayed():
        #     self.errors.append("Popup about changing fee error is not shown")
        # wallet.element_by_translation_id("continue-anyway").click()
        # if not send_transaction.element_by_text_part('0.000021 ETH').is_element_displayed():
        #     self.driver.fail("Custom fee is not applied!")
        self.errors.verify_no_errors()


class TestTransactionWalletMultipleDevice(MultipleDeviceTestCase):

    @marks.testrail_id(6330)
    @marks.medium
    @marks.transaction
    def test_can_send_all_tokens_via_max_option(self):
        sender = transaction_senders['V']
        receiver = transaction_recipients['K']
        self.create_drivers(2)
        device_1, device_2 = SignInView(self.drivers[0]), SignInView(self.drivers[1])
        home_1, home_2 = device_1.recover_access(sender['passphrase']), device_2.recover_access(receiver['passphrase'])
        wallet_sender = home_1.wallet_button.click()
        wallet_receiver = home_2.wallet_button.click()

        if wallet_receiver.asset_by_name('STT').is_element_present(10):
            initial_balance = wallet_receiver.get_asset_amount_by_name("STT")
        else:
            initial_balance = '0'

        device_1.just_fyi("Sending token amount to device who will use Set Max option for token")
        amount = '0.012345678912345678'
        wallet_sender.send_transaction(asset_name='STT', amount=amount, recipient=receiver['address'])
        wallet_receiver.wait_balance_is_changed(asset='STT', initial_balance=initial_balance, scan_tokens=True)
        wallet_receiver.accounts_status_account.click()

        device_1.just_fyi("Send all tokens via Set Max option")
        send_transaction = wallet_receiver.send_transaction_button.click()
        send_transaction.select_asset_button.click()
        asset_name = 'STT'
        asset_button = send_transaction.asset_by_name(asset_name)
        send_transaction.select_asset_button.click_until_presence_of_element(
            send_transaction.eth_asset_in_select_asset_bottom_sheet_button)
        asset_button.click()
        send_transaction.set_max_button.click()
        send_transaction.set_recipient_address(sender['address'])
        send_transaction.sign_transaction_button.click()
        send_transaction.sign_transaction()
        wallet_receiver.close_button.click()
        initial_balance = float(initial_balance) + float(amount)
        wallet_receiver.wait_balance_is_changed(asset='STT', initial_balance=str(initial_balance), scan_tokens=True)

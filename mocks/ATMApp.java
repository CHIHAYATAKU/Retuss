public class ATMApp {
    public static void main(String[] args) {
        BankAccount account = new BankAccount("123456789", 500.0);
        ATM atm = new ATM(account);
        atm.showMenu();
    }
}

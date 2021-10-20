package Server;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class Account {
    private String userName;
    private Set<Account> blockList;
    private Boolean online = false;
    private LocalDateTime lastLogin = null;
    private LocalDateTime lastActive = null;
    private String passWord;
    private LocalDateTime lockedUntil = null;


    Account(String username, String password) {
        userName = username;
        passWord = password;
        blockList = new HashSet<>();
    }

    /**
     * 
     * @param key
     * @return 0 for successful,
     * 1 for incorrect password
     * 2 for already logged in
     * 3 for currently locked
     */
    public synchronized int login(String key) {
        // if the account is locked, regardless of the password entered, notice the client
        if (isLocked()) return 3;
        if (key.equals(passWord)) {
            // if the password entered is right, but the account is logged in, notice the client
            if (online) return 2;
            // log this account in if it is not currently locked, the password entered is correct and it is not already logged in
            else {
                lastLogin = LocalDateTime.now();
                online = true;
                return 0;
            }
        } else return 1;
    }

    public void noticeActive() {
        lastActive = LocalDateTime.now();
    }

    public void noticeLocked(int second) {
        lockedUntil = LocalDateTime.now();
        lockedUntil = lockedUntil.plusSeconds(second);
    }

    public void logout() {
        online = false;
    }

    public LocalDateTime getLastLogin() {
        return lastLogin;
    }
    
    private Boolean isLocked() {
        if (lockedUntil != null) {
            return LocalDateTime.now().isBefore(lockedUntil);
        } else return false;
    }

    public String getUsername() {
        return userName;
    }

    // add the given account to this account's blocking list
    public void block(Account blocked) {
        blockList.add(blocked);
    }

    // remove the given account from this account's blocking list
    public void unblock(Account blocked) {
        blockList.remove(blocked);
    }

    // check if given account has been blocked by this account
    public Boolean ifblocked(Account act) {
        return blockList.contains(act);
    }
    
    public Boolean isLoggedInWithin(int sec) {
        return !LocalDateTime.now().isAfter(lastLogin.plusSeconds(sec));
    }
}

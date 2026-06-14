
import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;

public class Server {

    static Connection conn;

    public static void main(String[] args) throws Exception {

        Class.forName("com.mysql.cj.jdbc.Driver");

        conn = DriverManager.getConnection(
            "jdbc:mysql://localhost:3306/voting_db",
            "root",
            "sa123"
        );

        ServerSocket server = new ServerSocket(8080);
        System.out.println("Server running on http://localhost:8080");

        while (true) {
            Socket socket = server.accept();

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            String request = in.readLine();
            if (request == null) continue;

            if (request.contains("/register")) handleRegister(in, out);
            else if (request.contains("/login")) handleLogin(in, out);
            else if (request.contains("/kyc")) handleKYC(in, out);
            else if (request.contains("/vote")) handleVote(in, out);
            else if (request.contains("/results")) handleResults(out);
            else if (request.contains("/reset")) handleReset(out);

            socket.close();
        }
    }

    static String readBody(BufferedReader in) throws Exception {
        String line;
        int len = 0;

        while (!(line = in.readLine()).isEmpty()) {
            if (line.startsWith("Content-Length")) {
                len = Integer.parseInt(line.split(": ")[1]);
            }
        }

        char[] body = new char[len];
        in.read(body, 0, len);
        return new String(body);
    }

    static Map<String,String> parse(String body) {
        Map<String,String> map = new HashMap<>();
        for (String p : body.split("&")) {
            String[] kv = p.split("=");
            if (kv.length == 2) map.put(kv[0], kv[1]);
        }
        return map;
    }

    static void send(BufferedWriter out, String msg) throws Exception {
        out.write("HTTP/1.1 200 OK\r\n");
        out.write("Access-Control-Allow-Origin: *\r\n");
        out.write("Content-Type: text/plain\r\n\r\n");
        out.write(msg);
        out.flush();
    }

    static void handleRegister(BufferedReader in, BufferedWriter out) throws Exception {
        Map<String,String> d = parse(readBody(in));
        try {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users VALUES(?,?,?,false,false)"
            );
            ps.setString(1, d.get("username"));
            ps.setString(2, d.get("aadhaar"));
            ps.setString(3, d.get("password"));
            ps.executeUpdate();
            send(out,"success");
        } catch(Exception e){
            send(out,"error");
        }
    }

    static void handleLogin(BufferedReader in, BufferedWriter out) throws Exception {
        Map<String,String> d = parse(readBody(in));

        PreparedStatement ps = conn.prepareStatement(
            "SELECT * FROM users WHERE username=? AND aadhaar=? AND password=?"
        );
        ps.setString(1,d.get("username"));
        ps.setString(2,d.get("aadhaar"));
        ps.setString(3,d.get("password"));

        ResultSet rs = ps.executeQuery();
        send(out, rs.next() ? "success" : "fail");
    }

    static void handleKYC(BufferedReader in, BufferedWriter out) throws Exception {
        Map<String,String> d = parse(readBody(in));

        PreparedStatement ps = conn.prepareStatement(
            "SELECT * FROM users WHERE username=? AND aadhaar=?"
        );
        ps.setString(1,d.get("username"));
        ps.setString(2,d.get("aadhaar"));

        ResultSet rs = ps.executeQuery();

        if(rs.next()){
            PreparedStatement up = conn.prepareStatement(
                "UPDATE users SET kyc=true WHERE username=?"
            );
            up.setString(1,d.get("username"));
            up.executeUpdate();
            send(out,"success");
        } else send(out,"fail");
    }

    static void handleVote(BufferedReader in, BufferedWriter out) throws Exception {
        Map<String,String> d = parse(readBody(in));

        PreparedStatement check = conn.prepareStatement(
            "SELECT voted FROM users WHERE username=?"
        );
        check.setString(1,d.get("username"));
        ResultSet rs = check.executeQuery();

        if(rs.next() && rs.getBoolean("voted")){
            send(out,"already");
            return;
        }

        PreparedStatement vote = conn.prepareStatement(
            "UPDATE votes SET count=count+1 WHERE party=?"
        );
        vote.setString(1,d.get("party"));
        vote.executeUpdate();

        PreparedStatement mark = conn.prepareStatement(
            "UPDATE users SET voted=true WHERE username=?"
        );
        mark.setString(1,d.get("username"));
        mark.executeUpdate();

        send(out,"done");
    }

    static void handleResults(BufferedWriter out) throws Exception {
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery("SELECT * FROM votes");

        StringBuilder result = new StringBuilder();
        while(rs.next()){
            result.append(rs.getString("party"))
                  .append(":")
                  .append(rs.getInt("count"))
                  .append(",");
        }
        send(out,result.toString());
    }

    static void handleReset(BufferedWriter out) throws Exception {
        Statement st = conn.createStatement();
        st.executeUpdate("DELETE FROM users");
        st.executeUpdate("UPDATE votes SET count=0");
        send(out,"reset");
    }
}
package network;
import game.Map;
import game.User;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.logging.Logger;

import org.json.simple.JSONObject;

import packet.CTSHeader;
import packet.Packet;
import database.DataBase;
import database.GameData;

public final class Handler extends ChannelInboundHandlerAdapter {

	private static HashMap<ChannelHandlerContext, User>  user = new HashMap<ChannelHandlerContext, User>();
	private static HashMap<Integer, Map> map = new HashMap<Integer, Map>();
    private static Logger logger = Logger.getLogger(Handler.class.getName());
    
    @Override
	public void channelRead (ChannelHandlerContext ctx, Object msg) {
    	JSONObject packet = (JSONObject) msg;
    	switch ((int) packet.get("header")) {
	    	case CTSHeader.LOGIN:
	    		login(ctx, packet); break;
	    	case CTSHeader.REGISTER:
	    		register(ctx, packet); break;
	    	case CTSHeader.MOVE_CHARACTER:
	    		moveUser(ctx, packet); break;
	    	case CTSHeader.REMOVE_EQUIP_ITEM:
	    		removeEquipItem(ctx, packet); break;
	    	case CTSHeader.USE_STAT_POINT:
	    		useStatPoint(ctx, packet); break;
	    	case CTSHeader.OPEN_REGISTER_WINDOW:
	    		openRegisterWindow(ctx); break;
	    	case CTSHeader.OPEN_INVENTORY:
	    		break;
    	}
    }

    void login(ChannelHandlerContext ctx, JSONObject packet) {
    	String readID = (String) packet.get("id");
    	String readPass = (String) packet.get("pass");
    	
    	if (readID.equals("") || readPass.equals(""))
    		return;

		try {
			ResultSet rs = DataBase.executeQuery("SELECT * FROM `user` WHERE `id` = '" + readID + "';");
 
	    	if (rs.next()) {
	    		if (readPass.equals(rs.getString("pass"))) {
	    			user.put(ctx, new User(ctx,
	    					rs.getInt("no"), 
	    					rs.getString("id"), 
	    					rs.getString("pass"), 
	    					rs.getString("name"),
	    					rs.getInt("title"), 
	    					rs.getString("mail"), 
	    					rs.getString("image"), 
	    					rs.getInt("job"),
	    					rs.getInt("str"), 
	    					rs.getInt("dex"), 
	    					rs.getInt("agi"), 
	    					rs.getInt("stat_point"), 
	    					rs.getInt("skill_point"), 
	    					rs.getInt("hp"), 
	    					rs.getInt("mp"), 
	    					rs.getInt("level"), 
	    					rs.getInt("exp"), 
	    					rs.getInt("gold"), 
	    					rs.getInt("map"), 
	    					rs.getInt("x"), 
	    					rs.getInt("y"), 
	    					rs.getInt("direction"), 
	    					rs.getInt("speed"),
	    					rs.getInt("admin")));
	    			
	    			User u = user.get(ctx);
	    			u.loadData();
	    			
	    			ctx.writeAndFlush(Packet.loginMessage(u));
	    	    	map.get(u.getMap()).sendMessage(Packet.createCharacter(u));
	    	    	map.get(u.getMap()).addUser(u);
	    		} else {
	    			ctx.writeAndFlush(Packet.loginMessage(1));
	    		}
	    	} else {
				ctx.writeAndFlush(Packet.loginMessage(1));
	    	}
		} catch (SQLException e) {
			ctx.writeAndFlush(Packet.loginMessage(2));
			logger.warning(e.toString());
		}
    }
    
    void register(ChannelHandlerContext ctx, JSONObject packet) {
    	String readID = (String) packet.get("id");
    	String readPass = (String) packet.get("pass");
    	String readName = (String) packet.get("name");
    	String readMail = (String) packet.get("mail");
    	String readImage = (String) packet.get("image");
    	int readJob = (int) packet.get("job");
    	
    	if (readID.equals("") || readPass.equals("") || readName.equals("") || readMail.equals("") || readImage.equals(""))
    		return;
    	
    	boolean isIncludeInRegister = false;
    	for (GameData.Register r : GameData.register.values()) {
    		if (r.getJob() == readJob)
    			isIncludeInRegister = true;
    	}
    	if (!isIncludeInRegister)
    		return;

		try {
			ResultSet checkID = DataBase.executeQuery("SELECT * FROM `user` WHERE `id` = '" + readID + "';");
	    	if (checkID.next()) {
	    		ctx.writeAndFlush(Packet.registerMessage(1));
	    		return;
	    	}
	    	ResultSet checkName = DataBase.executeQuery("SELECT * FROM `user` WHERE `name` = '" + readName + "';");
	    	if (checkName.next()) {
	    		ctx.writeAndFlush(Packet.registerMessage(2));
	    		return;
	    	}
		} catch (SQLException e) {
			ctx.writeAndFlush(Packet.loginMessage(3));
			logger.warning(e.toString());
			return;
		}
    	GameData.Register r = GameData.register.get(readJob);
    	DataBase.insertUser(readID, readPass, readName, readMail, readImage, readJob, r.getMap(), r.getX(), r.getY(), r.getLevel());
    	ctx.writeAndFlush(Packet.registerMessage(0));
    }
    
    void moveUser(ChannelHandlerContext ctx, JSONObject packet) {
    	switch ((int) packet.get("type")) {
	    	case 2:
	    		user.get(ctx).moveDown();
	    		break;
	    	case 4:
	    		user.get(ctx).moveLeft();
	    		break;
	    	case 6:
	    		user.get(ctx).moveRight();
	    		break;
	    	case 8:
	    		user.get(ctx).moveUp();
	    		break;
    	}
    }

    void removeEquipItem(ChannelHandlerContext ctx, JSONObject packet) {
		user.get(ctx).equipItem((int) packet.get("type"), 0);
    }
    
    void useStatPoint(ChannelHandlerContext ctx, JSONObject packet) {
    	user.get(ctx).useStatPoint((String) packet.get("stat"));
    }
    
    void openRegisterWindow(ChannelHandlerContext ctx) {
    	ctx.writeAndFlush(Packet.openRegisterWindow());
    }
    
    public static void loadMap(int num) throws IOException {
    	for (int i = 1; i <= num; i++)
    		map.put(i, new Map("./Map/MAP" + i + ".map"));
    	
		logger.info("맵 로드 완료.");
    }
    
    @Override
	public void channelRegistered (ChannelHandlerContext ctx) {
    	logger.info(ctx.channel().toString() + " Registered.");
    }
    
    @Override
	public void channelUnregistered (ChannelHandlerContext ctx) throws SQLException {
    	if (user.containsKey(ctx)) {
    		DataBase.updateUser(user.get(ctx));
    		user.remove(ctx);
    	}
    	logger.info(ctx.channel().toString() + " Unregistered.");
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.fireExceptionCaught(cause);
    }
    
    public static HashMap<Integer, Map> getMap() {
    	return map;
    }
    
}
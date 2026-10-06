import android.database.sqlite.SQLiteDatabase;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
public final class FixtureSql {
  public static void main(String[] args){
    SQLiteDatabase db=SQLiteDatabase.openDatabase(args[0],null,SQLiteDatabase.OPEN_READWRITE);
    try{db.execSQL(new String(Base64.getDecoder().decode(args[1]),StandardCharsets.UTF_8));System.out.println("fixture_written");}finally{db.close();}
  }
}

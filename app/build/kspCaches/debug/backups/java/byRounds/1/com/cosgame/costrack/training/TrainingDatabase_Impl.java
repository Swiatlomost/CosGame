package com.cosgame.costrack.training;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class TrainingDatabase_Impl extends TrainingDatabase {
  private volatile TrainingDao _trainingDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `training_samples` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `activityType` TEXT NOT NULL, `accX` REAL NOT NULL, `accY` REAL NOT NULL, `accZ` REAL NOT NULL, `gyroX` REAL NOT NULL, `gyroY` REAL NOT NULL, `gyroZ` REAL NOT NULL, `magnitude` REAL NOT NULL, `sessionId` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `training_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `activityType` TEXT NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER NOT NULL, `samplesCount` INTEGER NOT NULL, `missionDurationSeconds` INTEGER NOT NULL, `completed` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'ebc2873a9f4dab7452b95e4a9af6edbe')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `training_samples`");
        db.execSQL("DROP TABLE IF EXISTS `training_sessions`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsTrainingSamples = new HashMap<String, TableInfo.Column>(11);
        _columnsTrainingSamples.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrainingSamples.put("activityType", new TableInfo.Column("activityType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrainingSamples.put("accX", new TableInfo.Column("accX", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrainingSamples.put("accY", new TableInfo.Column("accY", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrainingSamples.put("accZ", new TableInfo.Column("accZ", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrainingSamples.put("gyroX", new TableInfo.Column("gyroX", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrainingSamples.put("gyroY", new TableInfo.Column("gyroY", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrainingSamples.put("gyroZ", new TableInfo.Column("gyroZ", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrainingSamples.put("magnitude", new TableInfo.Column("magnitude", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrainingSamples.put("sessionId", new TableInfo.Column("sessionId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrainingSamples.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTrainingSamples = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesTrainingSamples = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoTrainingSamples = new TableInfo("training_samples", _columnsTrainingSamples, _foreignKeysTrainingSamples, _indicesTrainingSamples);
        final TableInfo _existingTrainingSamples = TableInfo.read(db, "training_samples");
        if (!_infoTrainingSamples.equals(_existingTrainingSamples)) {
          return new RoomOpenHelper.ValidationResult(false, "training_samples(com.cosgame.costrack.training.TrainingSample).\n"
                  + " Expected:\n" + _infoTrainingSamples + "\n"
                  + " Found:\n" + _existingTrainingSamples);
        }
        final HashMap<String, TableInfo.Column> _columnsTrainingSessions = new HashMap<String, TableInfo.Column>(7);
        _columnsTrainingSessions.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrainingSessions.put("activityType", new TableInfo.Column("activityType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrainingSessions.put("startTime", new TableInfo.Column("startTime", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrainingSessions.put("endTime", new TableInfo.Column("endTime", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrainingSessions.put("samplesCount", new TableInfo.Column("samplesCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrainingSessions.put("missionDurationSeconds", new TableInfo.Column("missionDurationSeconds", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrainingSessions.put("completed", new TableInfo.Column("completed", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTrainingSessions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesTrainingSessions = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoTrainingSessions = new TableInfo("training_sessions", _columnsTrainingSessions, _foreignKeysTrainingSessions, _indicesTrainingSessions);
        final TableInfo _existingTrainingSessions = TableInfo.read(db, "training_sessions");
        if (!_infoTrainingSessions.equals(_existingTrainingSessions)) {
          return new RoomOpenHelper.ValidationResult(false, "training_sessions(com.cosgame.costrack.training.TrainingSession).\n"
                  + " Expected:\n" + _infoTrainingSessions + "\n"
                  + " Found:\n" + _existingTrainingSessions);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "ebc2873a9f4dab7452b95e4a9af6edbe", "dc0dc01644f0e23d9fb79cb412e4eec4");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "training_samples","training_sessions");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `training_samples`");
      _db.execSQL("DELETE FROM `training_sessions`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(TrainingDao.class, TrainingDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public TrainingDao trainingDao() {
    if (_trainingDao != null) {
      return _trainingDao;
    } else {
      synchronized(this) {
        if(_trainingDao == null) {
          _trainingDao = new TrainingDao_Impl(this);
        }
        return _trainingDao;
      }
    }
  }
}

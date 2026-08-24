package com.xkball.x3dmap.client.terrain.file;

import com.mojang.logging.LogUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@NonNullByDefault
public interface IMapFile {
    
    Logger LOGGER = LogUtils.getLogger();
    int MAGIC = 0x584B5247;
    int FILE_VERSION = 5;
    
    Path getFile(Path dir);
    
    boolean dirty();
    
    void read(RandomAccessFile raf) throws IOException;
    
    void write(RandomAccessFile raf,@Nullable RandomAccessFile oldFile) throws IOException;
    
    default void afterRead(){
    
    }
    
    default void afterWrite(){
    
    }
    
    default void load(Path dir){
        var file = this.getFile(dir);
        if(!file.toFile().exists()) return;
        var delete = false;
        try (var raf = new RandomAccessFile(file.toFile(), "r")) {
            var magic = raf.readInt();
            var version = raf.readInt();
            if(magic != MAGIC || version != FILE_VERSION) {
                LOGGER.warn("Invalid map file {} , mismatch magic number or file version or region pos {}:{}, {}:{}", file, MAGIC, magic, FILE_VERSION, version);
                delete = true;
            }
            else {
                raf.seek(0);
                this.read(raf);
            }
        } catch(Exception e){
            LOGGER.error("Failed to load File {}",file, e);
            delete = true;
        }
        if(delete) {
            file.toFile().delete();
        }
        this.afterRead();
    }
    
    default void save(Path dir){
        if(!this.dirty()) return;
        var file = this.getFile(dir);
        if(!dir.toFile().exists()){
            //noinspection ResultOfMethodCallIgnored
            dir.toFile().mkdirs();
        }
        var tempFile = new File(file.toFile().getAbsolutePath() + ".tmp");
        if(!file.toFile().exists()) {
            try (var raf = new RandomAccessFile(tempFile, "rw")){
                this.write(raf,null);
            } catch (Exception e){
                LOGGER.error("Failed to save file {}", tempFile, e);
                return;
            }
        }
        else {
            try (var raf = new RandomAccessFile(tempFile, "rw");
                 var old = new RandomAccessFile(file.toFile(), "r")) {
                this.write(raf,old);
            } catch (Exception e){
                LOGGER.error("Failed to save file {}", tempFile, e);
                return;
            }
        }
        try {
            Files.move(tempFile.toPath(), file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            LOGGER.error("Failed to move temp file {}", file.toFile().getAbsolutePath(), e);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tempFile.delete();
        }
        this.afterWrite();
    }
}

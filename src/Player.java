import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.UnsupportedTagException;
import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;

import java.awt.event.*;

import javazoom.jl.player.FactoryRegistry;
import support.PlayerWindow;
import support.Song;
import support.CustomFileChooser;

import javax.swing.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

public class Player {

    //private PlayerWindow playerWindow;
    /**
     * The MPEG audio bitstream.
     */
    private Bitstream bitstream;
    /**
     * The MPEG audio decoder.
     */
    private Decoder decoder;
    /**
     * The AudioDevice the audio samples are written to.
     */
    private AudioDevice device;

    private PlayerWindow window;


    private boolean repeat = false;
    private boolean shuffle = false;
    private boolean playerEnabled = false;
    private boolean playerPaused = true;
    private boolean isPlaying = false;
    private boolean paused = true;
    private Song currentSong;
    private Song selected;
    private Song newSong;
    private int currentFrame = 0;
    private int newFrame;
    private final Lock lock = new ReentrantLock();

    private ArrayList <String[]> listaDeMusicas = new ArrayList<>();
    private ArrayList <Song> listaDeSons = new ArrayList<>();
    String[][] queue = {};

    public Player() {
        ActionListener buttonListenerPlayNow = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String now = window.getSelectedSong();
                playNow(now);
            }
        };
        ActionListener buttonListenerRemove = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String removed = window.getSelectedSong();
                removeFromQueue(removed);
            }
        };
        ActionListener buttonListenerAddQueue = e -> addToQueue(newSong);
        ActionListener buttonListenerShuffle = e -> aux();
        ActionListener buttonListenerPrevious = e -> aux();
        ActionListener buttonListenerPlayPause = e -> playPause();
        ActionListener buttonListenerStop = e -> stop();
        ActionListener buttonListenerNext = e -> aux();
        ActionListener buttonListenerRepeat = e -> aux();

        MouseListener scrubberListenerClick = new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
            }

            @Override
            public void mousePressed(MouseEvent e) {
            }

            @Override
            public void mouseReleased(MouseEvent e) {
            }

            @Override
            public void mouseEntered(MouseEvent e) {
            }

            @Override
            public void mouseExited(MouseEvent e) {
            }
        };

        MouseMotionListener scrubberListenerMotion = new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
            }

            @Override
            public void mouseMoved(MouseEvent e) {
            }
        };

        this.window = new PlayerWindow("Spotify", this.queue, buttonListenerPlayNow,
                buttonListenerRemove, buttonListenerAddQueue, buttonListenerShuffle,
                buttonListenerPrevious, buttonListenerPlayPause, buttonListenerStop,
                buttonListenerNext, buttonListenerRepeat, scrubberListenerClick, scrubberListenerMotion);
    }

    //<editor-fold desc="Essential">


    /**
     * @return False if there are no more frames to play.
     */
    private boolean playNextFrame() throws JavaLayerException {
        Thread running = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    lock.lock();
                    if (device != null) {
                        Header h = bitstream.readFrame();
                        if (h == null) return;

                        SampleBuffer output = (SampleBuffer) decoder.decodeFrame(h, bitstream);
                        device.write(output.getBuffer(), 0, output.getBufferLength());
                        bitstream.closeFrame();
                    }
                    return;
                }
                catch (JavaLayerException e){
                    System.out.println(e);
                }
                finally {
                    lock.unlock();
                }

            }
        });
        running.start();
        return true;
    }

    /**
     * @return False if there are no more frames to skip.
     */
    private boolean skipNextFrame() throws BitstreamException {
        // TODO Is this thread safe?
        Header h = bitstream.readFrame();
        if (h == null) return false;
        bitstream.closeFrame();
        currentFrame++;
        return true;
    }

    /**
     * Skips bitstream to the target frame if the new frame is higher than the current one.
     *
     * @param newFrame Frame to skip to.
     * @throws BitstreamException
     */
    private void skipToFrame(int newFrame) throws BitstreamException {
        // TODO Is this thread safe?
        if (newFrame > currentFrame) {
            int framesToSkip = newFrame - currentFrame;
            boolean condition = true;
            while (framesToSkip-- > 0 && condition) condition = skipNextFrame();
        }
    }
    //</editor-fold>

    //<editor-fold desc="Queue Utilities">
    public void addToQueue(Song song) {
        Thread addThread = new Thread(new Runnable() {
            @Override
            public void run() {
                    try{
                        lock.lock();
                        newSong = window.getNewSong();
                        String[] metaDados = newSong.getDisplayInfo();
                        listaDeMusicas.add(metaDados);
                        listaDeSons.add(newSong);
                        changeQueue();
                    }
                    catch (java.io.IOException | BitstreamException | UnsupportedTagException | InvalidDataException exception){
                        System.out.println("error");
                    }
                    finally {
                        lock.unlock();
                    }
            }
        });
        addThread.start();
    }

    public void removeFromQueue(String filepath) {
        Thread removeThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    lock.lock();
                    String removed = new String();
                    for(int i = 0; i < listaDeMusicas.size(); i++){
                        if(listaDeMusicas.get(i)[5].equals(filepath)){  //Vai procurar na lista a musica que tem o mesmo filepath da que se deseja remover
                            removed = listaDeMusicas.get(i)[0];
                            listaDeMusicas.remove(i);
                        }
                    }
                    for(int j = 0; j < listaDeSons.size(); j++){
                        if(listaDeSons.get(j).getTitle().equals(removed)){
                            listaDeSons.remove(j);
                        }
                    }
                    changeQueue();
                }
                finally {
                    lock.unlock();
                }
            }
        });
        removeThread.start();
    }

    public void aux(){

    }

    public void changeQueue(){
        String[][] converter = new String[this.listaDeMusicas.size()][7]; //cria uma matriz, onde cada linha representa uma musica, e tem 7 colunas que são as categorias da musica
        this.queue = this.listaDeMusicas.toArray(converter); //Converter o array list em uma matriz de array e atualizar a fila
        window.updateQueueList(this.queue); // Bota a fila na tela

    }

    public String[][] getQueueAsArray() {
        return null;
    }

    //</editor-fold>

    //<editor-fold desc="Controls">
    public void playNow(String filePath) {
        Thread playing = new Thread(new Runnable() {
            @Override
            public void run() {
              try {
                  lock.lock();
                  for (int i = 0; i < listaDeSons.size(); i++) {
                      if (listaDeSons.get(i).getFilePath().equals(filePath)) {
                          currentSong = listaDeSons.get(i);
                      }
                  }
                  try {
                      device = FactoryRegistry.systemRegistry().createAudioDevice();
                      device.open(decoder = new Decoder());
                      bitstream = new Bitstream(currentSong.getBufferedInputStream());
                      playNextFrame();
                  }
                  catch (JavaLayerException | FileNotFoundException e){
                      System.out.println(e);
                  }
              }
              finally {
                  isPlaying = true;
                  paused = false;
                  window.updatePlayingSongInfo(currentSong.getTitle(), currentSong.getAlbum(), currentSong.getArtist());
                  window.updatePlayPauseButtonIcon(paused);
                  window.setEnabledScrubberArea(isPlaying);
                  lock.unlock();
              }
            }
        });
        playing.start();
    }

    public void stop() {
        isPlaying = false;
        paused = true;
        window.updatePlayPauseButtonIcon(paused);
        window.setEnabledScrubberArea(isPlaying);
    }

    public void playPause() {
        window.updatePlayPauseButtonIcon(paused);
    }

    public void resume() {
    }

    public void next() {
    }

    public void previous() {
    }


    //</editor-fold>

    //<editor-fold desc="Getters and Setters">

    //</editor-fold>

}

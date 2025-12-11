package Project.Common;

import java.util.List;

public class RoomResultPayload extends Payload {
    private List<String> rooms;

    public List<String> getRooms() {
        return rooms;
    }

    public void setRooms(List<String> rooms) {
        this.rooms = rooms;
    }
}

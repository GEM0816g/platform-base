package xyz.eulix.platform.services.push.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import xyz.eulix.platform.services.push.entity.PushTokenEntity;

import javax.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class PushTokenEntityRepository implements PanacheRepository<PushTokenEntity> {
    // ����client_uuid��ѯ��Դ
    private static final String FIND_BY_CLIENTUUID = "client_uuid=?1";

    // ����client_uuids��ѯ��Դ
    private static final String FIND_BY_CLIENTUUIDS = "client_uuid in (?1)";

    // ����id������Դ
    private static final String UPDATE_BY_ID = "deviceToken=?1, extra=?2, updated_at=now() where client_uuid=?3";

    public Optional<PushTokenEntity> findByClientUUID(String clientUUID) {
        return this.find(FIND_BY_CLIENTUUID, clientUUID).singleResultOptional();
    }

    public void updateByClientUUID(String clientUUID, String deviceToken, String extra) {
        this.update(UPDATE_BY_ID, deviceToken, extra, clientUUID);
    }

    public List<PushTokenEntity> findByClientUUIDs(Collection<String> clientUUIDs) {
        return this.find(FIND_BY_CLIENTUUIDS, clientUUIDs).list();
    }
}

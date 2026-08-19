package io.hyperfoil.tools.h5m.entity;

import io.hyperfoil.tools.h5m.api.Role;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.ArrayList;
import java.util.List;

@Entity(name = "h5m_user")
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"sub", "iss"}))
public class UserEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String sub;

    public String iss;

    @Column(unique = true)
    public String username;

    @Enumerated(EnumType.STRING)
    public Role role;

    @ManyToMany(mappedBy = "members")
    public List<TeamEntity> teams = new ArrayList<>();

    public UserEntity() {}

    public UserEntity(String username, Role role) {
        this.username = username;
        this.role = role;
    }

    public UserEntity(String sub, String iss, String username, Role role) {
        this.sub = sub;
        this.iss = iss;
        this.username = username;
        this.role = role;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof UserEntity that)) {
            return false;
        }
        return username.equals(that.username);
    }

    @Override
    public int hashCode() {
        return username.hashCode();
    }

    @Override
    public String toString() {
        return "UserEntity<" + id + ">[ username=" + username + " role=" + role + " ]";
    }
}

package com.team.unanimous.service;


import com.team.unanimous.dto.requestDto.BanRequestDto;
import com.team.unanimous.dto.requestDto.NicknameRequestDto;
import com.team.unanimous.dto.requestDto.TeamInviteRequestDto;
import com.team.unanimous.dto.requestDto.TeamRequestDto;
import com.team.unanimous.dto.responseDto.*;
import com.team.unanimous.exceptionHandler.CustomException;
import com.team.unanimous.exceptionHandler.ErrorCode;
import com.team.unanimous.model.Image;
import com.team.unanimous.model.TeamImage;
import com.team.unanimous.model.team.Team;
import com.team.unanimous.model.team.TeamUser;
import com.team.unanimous.model.user.User;
import com.team.unanimous.repository.TeamImageRepository;
import com.team.unanimous.repository.team.TeamRepository;
import com.team.unanimous.repository.team.TeamUserRepository;
import com.team.unanimous.repository.user.UserRepository;
import com.team.unanimous.security.UserDetailsImpl;
import com.team.unanimous.service.S3.S3Uploader;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.transaction.Transactional;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    private final TeamUserRepository teamUserRepository;
    private final S3Uploader s3Uploader;
    private final TeamImageRepository teamImageRepository;

    // Unanimous 참여하기
    @Transactional
    public ResponseEntity joinUnanimous(UserDetailsImpl userDetails){
        String teamname = "Unanimous";
        Team team = teamRepository.findTeamByTeamname(teamname);
        if (team == null){
            throw new CustomException(ErrorCode.TEAM_NOT_FOUND);
        }
        User user = userRepository.findUserById(userDetails.getUser().getId());
        List<TeamUser> teamUserList = teamUserRepository.findAllByUser(user);
        // 인덱스가 0부터 시작하기 때문에, 5개 제한이면 size()>4 로 해주어야 한다.
        if (teamUserList.size()>4){
            throw new CustomException(ErrorCode.EXCESS_TEAM_NUMBER);
        }
        TeamUser teamUser1 = teamUserRepository.findByTeam(team);
        if (teamUser1.getUser().getId().equals(userDetails.getUser().getId())){
            throw new CustomException(ErrorCode.DUPLICATE_TEAM_USER);
        }
        TeamUser teamuser = new TeamUser(user,team);
        teamUserRepository.save(teamuser);

        return ResponseEntity.ok("팀에 가입되었습니다!");

    }
    //팀 생성
    @Transactional
    public ResponseEntity createTeam(TeamRequestDto requestDto, UserDetailsImpl userDetails){
        User user = userRepository.findUserById(userDetails.getUser().getId());
        if (user == null){
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        List<TeamUser> teamUserList = teamUserRepository.findAllByUser(user);
        // 인덱스가 0부터 시작하기 때문에, 5개 제한이면 size()>4 로 해주어야 한다.
        if(teamUserList.size()>4){
            throw new CustomException(ErrorCode.EXCESS_TEAM_NUMBER);
        }
        String teamname1 = requestDto.getTeamname();
        if (teamRepository.findByTeamname(teamname1).isPresent()){
            throw new CustomException(ErrorCode.DUPLICATE_TEAM_NAME);
        }
        Team team = Team.builder()
                .teamname(requestDto.getTeamname())
                .uuid(UUID.randomUUID().toString())
                .teamManager(userDetails.getUser().getNickname())
                .build();
        teamRepository.save(team);
        TeamUser teamUser = new TeamUser(user,team);
        teamUserRepository.save(teamUser);
        return ResponseEntity.ok("팀이 생성되었습니다!");
    }

    //팀 선택 페이지
    @Transactional
    public List<TeamUserResponseDto> getTeams(UserDetailsImpl userDetails){
        User user = userRepository.findUserById(userDetails.getUser().getId());
        List<TeamUser> teamUserList = teamUserRepository.findAllByUser(user);

        List<TeamUserResponseDto> responseDtoList = new ArrayList<>();

        for (TeamUser teamUser : teamUserList) {
                TeamUserResponseDto responseDto = new TeamUserResponseDto(teamUser.getTeam());
                responseDtoList.add(responseDto);
        }

        return responseDtoList;

    }

    //초대받은 팀 찾기
    @Transactional
    public TeamResponseDto findTeamUuid(TeamInviteRequestDto requestDto){
        String uuid = requestDto.getUuid();
        Team team = teamRepository.findByUuid(uuid);
        if (team == null){
            throw new CustomException(ErrorCode.TEAM_NOT_FOUND);
        }

        return new TeamResponseDto(team.getId(), team.getTeamname(), team.getUuid());
    }

    //팀 가입하기
    @Transactional
    public ResponseEntity JoinTeam(TeamInviteRequestDto requestDto, UserDetailsImpl userDetails){
        String uuid = requestDto.getUuid();
        Team team = teamRepository.findByUuid(uuid);

        if (team == null){
            throw new CustomException(ErrorCode.TEAM_NOT_FOUND);
        }
        User user = userRepository.findUserById(userDetails.getUser().getId());
        List<TeamUser> teamUserList = teamUserRepository.findAllByUser(user);
        // 인덱스가 0부터 시작하기 때문에, 5개 제한이면 size()>4 로 해주어야 한다.
        if (teamUserList.size()>4){
            throw new CustomException(ErrorCode.EXCESS_TEAM_NUMBER);
        }
        TeamUser teamUser1 = teamUserRepository.findByTeam(team);
        if (teamUser1.getUser().getId().equals(userDetails.getUser().getId())){
            throw new CustomException(ErrorCode.DUPLICATE_TEAM_USER);
        }
        TeamUser teamuser = new TeamUser(user,team);
        teamUserRepository.save(teamuser);

        return ResponseEntity.ok("팀에 가입되었습니다!");
    }

    // 팀 메인 게시판
    @Transactional
    public TeamUserMainResponseDto getMain(Long teamId){
        Team team = teamRepository.findTeamById(teamId);
        List<TeamUser> teamUserList = teamUserRepository.findAllByTeam(team);
        List<NicknameResponseDto> nicknameResponseDtos = new ArrayList<>();
        for (TeamUser teamUser : teamUserList){
            NicknameResponseDto nicknameResponseDto = new NicknameResponseDto(teamUser.getUser());
            nicknameResponseDtos.add(nicknameResponseDto);
        }

        TeamUserMainResponseDto teamUserMainResponseDto = new TeamUserMainResponseDto(team,nicknameResponseDtos);
        return teamUserMainResponseDto;
    }

    // 팀 프로필 사진, 팀 네임
    @Transactional
    public ResponseEntity updateTeam(MultipartFile multipartFile, Long teamId, TeamRequestDto requestDto, UserDetailsImpl userDetails)throws IOException {
        Team team = teamRepository.findTeamById(teamId);
        if (team == null){
            throw new CustomException(ErrorCode.TEAM_NOT_FOUND);
        }
        if (!(userDetails.getUser().getNickname().equals(team.getTeamManager()))){
            throw new CustomException(ErrorCode.TEAM_MANAGER_CONFLICT);
        }
//        TeamImage teamImage = new TeamImage(s3Uploader.upload(multipartFile, "teamImage"));
//        teamImageRepository.save(teamImage);

        team.updateTeam(requestDto);
//        team.updateImage(teamImage);
        return ResponseEntity.ok("팀 프로필 수정 완료");
    }

    // 팀원 강퇴
    @Transactional
    public ResponseEntity banUser(Long teamId, BanRequestDto requestDto,UserDetailsImpl userDetails){
        String nickname = requestDto.getNickname();
        Team team = teamRepository.findTeamById(teamId);
        User user = userRepository.findUserByNickname(nickname);
        if (user == null){
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        if (!(userDetails.getUser().getNickname().equals(team.getTeamManager()))){
            throw new CustomException(ErrorCode.INVALID_AUTHORITY);
        }
        if (user.getNickname().equals(team.getTeamManager())){
            throw new CustomException(ErrorCode.TEAM_MANAGER_CONFLICT);
        }
        TeamUser teamUser = teamUserRepository.findAllByTeamIdAndUserId(teamId, user.getId());
        teamUserRepository.delete(teamUser);

        return ResponseEntity.ok("팀원 추방 완료");
    }

    // 팀 탈퇴
    @Transactional
    public ResponseEntity exitTeam(Long teamId,
                                   BanRequestDto requestDto,
                                   UserDetailsImpl userDetails){
        String nickname = requestDto.getNickname();
        Team team = teamRepository.findTeamById(teamId);
        User user = userRepository.findUserByNickname(nickname);
        if (user == null){
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        if (!(team.getTeamManager().equals(userDetails.getUser().getNickname()))){
            throw new CustomException(ErrorCode.INVALID_AUTHORITY);
        }
        if (nickname.equals(team.getTeamManager())){
            throw new CustomException(ErrorCode.TEAM_MANAGER_CONFLICT);
        }
        TeamUser teamUser = teamUserRepository.findAllByTeamIdAndUserId(teamId, user.getId());
        teamUserRepository.delete(teamUser);

        return ResponseEntity.ok("팀 탈퇴 완료");
    }

    // 팀장 위임
    @Transactional
    public ResponseEntity changeTeamManager(NicknameRequestDto nicknameRequestDto,
                                            Long teamId,
                                            UserDetailsImpl userDetails){
        String user = nicknameRequestDto.getNickname();
        if (user == null){
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        Team team = teamRepository.findTeamById(teamId);
        if (!(userDetails.getUser().getNickname().equals(team.getTeamManager()))){
            throw new CustomException(ErrorCode.INVALID_AUTHORITY);
        }


        team.setTeamManager(user);
        teamRepository.save(team);
        return ResponseEntity.ok(user+" 님이"+team.getTeamname()+" 팀의 팀장이 되었습니다");
    }
}
